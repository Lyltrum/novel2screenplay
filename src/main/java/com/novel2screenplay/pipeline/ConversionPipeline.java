package com.novel2screenplay.pipeline;

import com.novel2screenplay.assemble.ScreenplayAssembler;
import com.novel2screenplay.episode.EpisodePlanner;
import com.novel2screenplay.episode.EpisodeValidator;
import com.novel2screenplay.model.Episode;
import com.novel2screenplay.style.StyleTemplate;
import com.novel2screenplay.validate.ValidationIssue;
import com.novel2screenplay.bible.StoryBibleService;
import com.novel2screenplay.extract.ChapterSynopsisService;
import com.novel2screenplay.extract.SceneExtractionService;
import com.novel2screenplay.extract.TitleLoglineService;
import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.StoryBible;
import com.novel2screenplay.model.extraction.TitleLogline;
import com.novel2screenplay.split.ChapterSplitter;
import com.novel2screenplay.split.ChunkSplitter;
import com.novel2screenplay.validate.RepairService;
import com.novel2screenplay.validate.SceneValidator;
import com.novel2screenplay.validate.ValidationReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 只负责：编排小说→剧本的完整流水线（各步骤的调用顺序），自身不含具体抽取逻辑。
 * <p>
 * 顺序：切章 → 并行登记建立完整 StoryBible → 并行预算各章梗概(前情提要)
 *      → 按章并行抽场景(章内多块串行) → 生成剧名/梗概 → 组装并全局编号 → 自检修复 →(剧集)分集。
 * 先建完整登记表保跨章人名一致；前情提要由预算好的章级梗概拼成，
 * 从而让抽取摆脱"等前面章节抽完"的串行依赖、可并行提速，且不丢跨章连贯。
 * 进度经 {@link ProgressListener} 实时回调（供 SSE 流式输出）。
 */
@Component
public class ConversionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ConversionPipeline.class);
    private static final String DEFAULT_STYLE = "剧集";
    /** 自检修复闭环的最大轮数，避免无界循环与 token 浪费。 */
    private static final int MAX_REPAIR_ROUNDS = 2;
    /** 滚动提要保留的最大字符数（取最近剧情，控制 token）。 */
    private static final int SYNOPSIS_MAX_CHARS = 800;
    /** 登记/梗概等轻量调用的并发上限——给百炼留 QPS 余量，避免触发限流。 */
    private static final int PREP_CONCURRENCY = 5;
    /** 场景抽取（重输出）的并发上限——比轻量调用更保守，降低限流导致整章丢失的风险。 */
    private static final int EXTRACT_CONCURRENCY = 4;

    private final ChapterSplitter chapterSplitter;
    private final ChunkSplitter chunkSplitter;
    private final StoryBibleService storyBibleService;
    private final ChapterSynopsisService chapterSynopsisService;
    private final SceneExtractionService sceneExtractionService;
    private final TitleLoglineService titleLoglineService;
    private final ScreenplayAssembler screenplayAssembler;
    private final SceneValidator sceneValidator;
    private final RepairService repairService;
    private final StyleTemplate styleTemplate;
    private final EpisodePlanner episodePlanner;
    private final EpisodeValidator episodeValidator;

    public ConversionPipeline(ChapterSplitter chapterSplitter,
                              ChunkSplitter chunkSplitter,
                              StoryBibleService storyBibleService,
                              ChapterSynopsisService chapterSynopsisService,
                              SceneExtractionService sceneExtractionService,
                              TitleLoglineService titleLoglineService,
                              ScreenplayAssembler screenplayAssembler,
                              SceneValidator sceneValidator,
                              RepairService repairService,
                              StyleTemplate styleTemplate,
                              EpisodePlanner episodePlanner,
                              EpisodeValidator episodeValidator) {
        this.chapterSplitter = chapterSplitter;
        this.chunkSplitter = chunkSplitter;
        this.storyBibleService = storyBibleService;
        this.chapterSynopsisService = chapterSynopsisService;
        this.sceneExtractionService = sceneExtractionService;
        this.titleLoglineService = titleLoglineService;
        this.screenplayAssembler = screenplayAssembler;
        this.sceneValidator = sceneValidator;
        this.repairService = repairService;
        this.styleTemplate = styleTemplate;
        this.episodePlanner = episodePlanner;
        this.episodeValidator = episodeValidator;
    }

    /** 同步转换（不关心进度）。 */
    public ConversionResult convert(String novelText, String style, Integer episodes) {
        return convert(novelText, style, episodes, ProgressListener.NOOP);
    }

    /**
     * 转换主流程，{@code progress} 实时回调各阶段进展（供 SSE 流式输出）。
     * <p>
     * 提速策略：登记、章级梗概均"各章独立、可并行"先算好；场景抽取因此摆脱
     * "等前面章节抽完"的串行依赖，改为按章并行（章内多块仍串行保局部连贯）——
     * 跨章连贯靠预算好的章级梗概拼成的前情提要，跨章人名一致靠完整 bible，质量不降。
     */
    public ConversionResult convert(String novelText, String style, Integer episodes,
                                    ProgressListener progress) {
        String effectiveStyle = (style == null || style.isBlank()) ? DEFAULT_STYLE : style.strip();

        List<Chapter> chapters = chapterSplitter.split(novelText);
        log.info("切分出 {} 章", chapters.size());
        progress.onProgress("split", "切分出 " + chapters.size() + " 章");

        // ① 逐章登记（各章独立，并行抽取再按章序合并，结果与串行逐字相同）。
        StoryBible bible = StoryBible.empty();
        for (StoryBible delta : extractBibleDeltas(chapters)) {
            bible = storyBibleService.merge(bible, delta);
        }
        log.info("登记表建立完成：人物 {} 位，地点 {} 处",
                bible.characters().size(), bible.locations().size());
        progress.onProgress("bible",
                "登记表建立完成：人物 " + bible.characters().size() + " 位、地点 " + bible.locations().size() + " 处");

        // ② 预算每章一句话梗概（各章独立，并行）——拼成前情提要，解开抽取的串行依赖。
        List<String> chapterSynopses = summarizeChapters(chapters);
        progress.onProgress("synopsis", "前情提要预算完成（" + chapters.size() + " 章）");

        // ③ 按章并行抽场景：每章注入 完整bible + 前情(前面各章梗概)；章内多块仍串行。
        List<Scene> scenes = new ArrayList<>();
        for (List<Scene> chapterScenes : extractScenesInParallel(chapters, bible, effectiveStyle, chapterSynopses, progress)) {
            scenes.addAll(chapterScenes);
        }

        scenes = dedupByExcerpt(scenes);
        if (scenes.isEmpty()) {
            throw new ConversionException("未能从小说中抽取出任何场景（模型调用可能全部失败），请稍后重试");
        }
        progress.onProgress("extract", "场景抽取完成，共 " + scenes.size() + " 个场景");

        // 剧名/梗概生成失败用占位文本兜底，不阻断整本输出（残留问题会在校验中体现）
        TitleLogline titleLogline;
        try {
            titleLogline = titleLoglineService.generate(scenes, effectiveStyle);
        } catch (Exception e) {
            log.warn("剧名/梗概生成失败，使用占位文本：{}", e.getMessage());
            titleLogline = new TitleLogline("未命名剧本", "");
        }

        Screenplay screenplay = screenplayAssembler.assemble(
                chapters.size(),
                titleLogline.title(),
                titleLogline.logline(),
                effectiveStyle,
                bible.characters(),
                scenes);
        progress.onProgress("assemble", "组装完成：《" + screenplay.title() + "》");

        ConversionResult result = selfHealing(screenplay);
        progress.onProgress("validate", "自检修复完成，残留告警 " + result.report().count() + " 处");

        // 剧集形态：在场景之上叠加"集"编排（分集大纲 + 集尾钩子）；单本形态(电影/话剧/短剧/分镜)跳过
        if (styleTemplate.isSeries(effectiveStyle)) {
            result = planEpisodes(result, episodes);
            int epCount = result.screenplay().episodes() == null ? 0 : result.screenplay().episodes().size();
            progress.onProgress("episode", "分集完成，共 " + epCount + " 集");
        }
        return result;
    }

    /**
     * 并行抽取各章的人物/地点 delta，返回与 chapters 同序的结果列表（缺失章用空 delta 占位）。
     * 抽取调用彼此独立，合并仍由调用方按章序串行完成 ⟹ 等价于串行登记、零质量损失。
     */
    private List<StoryBible> extractBibleDeltas(List<Chapter> chapters) {
        int poolSize = Math.min(PREP_CONCURRENCY, Math.max(1, chapters.size()));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<StoryBible>> futures = new ArrayList<>(chapters.size());
            for (Chapter chapter : chapters) {
                futures.add(pool.submit(() -> {
                    try {
                        return storyBibleService.extractDelta(chapter);
                    } catch (Exception e) {
                        log.warn("第 {} 章人物/地点登记失败，跳过本章登记：{}", chapter.index(), e.getMessage());
                        return StoryBible.empty();
                    }
                }));
            }
            List<StoryBible> deltas = new ArrayList<>(futures.size());
            for (Future<StoryBible> f : futures) {   // Future 按提交顺序，f.get() 即按章序
                try {
                    deltas.add(f.get());
                } catch (Exception e) {
                    deltas.add(StoryBible.empty());
                }
            }
            return deltas;
        } finally {
            pool.shutdown();
        }
    }

    /** 并行预算各章一句话梗概，返回与 chapters 同序的列表（失败章留空字符串）。 */
    private List<String> summarizeChapters(List<Chapter> chapters) {
        int poolSize = Math.min(PREP_CONCURRENCY, Math.max(1, chapters.size()));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        try {
            List<Future<String>> futures = new ArrayList<>(chapters.size());
            for (Chapter chapter : chapters) {
                futures.add(pool.submit(() -> {
                    try {
                        return chapterSynopsisService.summarize(chapter);
                    } catch (Exception e) {
                        log.warn("第 {} 章梗概预算失败，前情留空：{}", chapter.index(), e.getMessage());
                        return "";
                    }
                }));
            }
            List<String> synopses = new ArrayList<>(futures.size());
            for (Future<String> f : futures) {
                try {
                    synopses.add(f.get());
                } catch (Exception e) {
                    synopses.add("");
                }
            }
            return synopses;
        } finally {
            pool.shutdown();
        }
    }

    /**
     * 按章并行抽取场景，返回与 chapters 同序的"每章场景列表"。
     * 各章互不依赖（前情已由预算好的章级梗概提供），抽取调用可重叠；某章整体失败用空列表占位。
     */
    private List<List<Scene>> extractScenesInParallel(List<Chapter> chapters, StoryBible bible,
                                                      String style, List<String> chapterSynopses,
                                                      ProgressListener progress) {
        int poolSize = Math.min(EXTRACT_CONCURRENCY, Math.max(1, chapters.size()));
        ExecutorService pool = Executors.newFixedThreadPool(poolSize);
        AtomicInteger done = new AtomicInteger();
        try {
            List<Future<List<Scene>>> futures = new ArrayList<>(chapters.size());
            for (int i = 0; i < chapters.size(); i++) {
                Chapter chapter = chapters.get(i);
                String recap = buildRecap(chapterSynopses, i);   // 前面各章的章级梗概
                futures.add(pool.submit(() -> {
                    List<Scene> chapterScenes = extractChapter(chapter, bible, style, recap);
                    int k = done.incrementAndGet();
                    log.info("第 {} 章抽出 {} 个场景（{}/{}）", chapter.index(), chapterScenes.size(), k, chapters.size());
                    progress.onProgress("extract",
                            "第 " + chapter.index() + " 章抽出 " + chapterScenes.size()
                                    + " 个场景（" + k + "/" + chapters.size() + "）");
                    return chapterScenes;
                }));
            }
            List<List<Scene>> results = new ArrayList<>(futures.size());
            for (Future<List<Scene>> f : futures) {   // 按提交顺序回收 ⟹ 按章序拼接
                try {
                    results.add(f.get());
                } catch (Exception e) {
                    results.add(List.of());
                }
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    /** 单章内逐块串行抽取：块间用"前情(章级梗概) + 本章已抽场景概要"做滚动提要保局部连贯。 */
    private List<Scene> extractChapter(Chapter chapter, StoryBible bible, String style, String recap) {
        List<String> chunks = chunkSplitter.split(chapter.text());
        List<Scene> chapterScenes = new ArrayList<>();
        for (String chunk : chunks) {
            Chapter chunkUnit = new Chapter(chapter.index(), chapter.title(), chunk);
            String synopsis = composeSynopsis(recap, chapterScenes);
            try {
                chapterScenes.addAll(sceneExtractionService.extract(chunkUnit, bible, style, synopsis));
            } catch (Exception e) {
                log.warn("第 {} 章某块场景抽取失败，跳过该块：{}", chapter.index(), e.getMessage());
            }
        }
        return chapterScenes;
    }

    /** 取前 {@code upto} 章的章级梗概拼成"前情提要"，超长则保留末尾（最近剧情）以控 token。 */
    private String buildRecap(List<String> chapterSynopses, int upto) {
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < upto && j < chapterSynopses.size(); j++) {
            String s = chapterSynopses.get(j);
            if (s != null && !s.isBlank()) {
                sb.append(s.strip()).append(' ');
            }
        }
        return tail(sb.toString().strip(), SYNOPSIS_MAX_CHARS);
    }

    /** 分集：调 EpisodePlanner 产出集编排，校验覆盖性并把集层告警并入报告（本期不做分集修复闭环）。 */
    private ConversionResult planEpisodes(ConversionResult result, Integer targetEpisodes) {
        Screenplay sp = result.screenplay();
        List<Episode> episodes = episodePlanner.plan(sp, targetEpisodes);
        Screenplay withEps = withEpisodes(sp, episodes);
        ValidationReport epReport = episodeValidator.validate(withEps);
        log.info("分集完成：{} 集，集层告警 {} 处", episodes.size(), epReport.count());
        return new ConversionResult(withEps, mergeReports(result.report(), epReport));
    }

    private Screenplay withEpisodes(Screenplay sp, List<Episode> episodes) {
        return new Screenplay(sp.meta(), sp.title(), sp.logline(), sp.style(),
                sp.characters(), episodes, sp.scenes());
    }

    private ValidationReport mergeReports(ValidationReport a, ValidationReport b) {
        List<ValidationIssue> merged = new ArrayList<>(a.issues());
        merged.addAll(b.issues());
        return new ValidationReport(merged);
    }

    /** 自检 + 自动修复闭环（亮点 P1）：体检 → 有问题则带清单让模型修 → 复检，最多 {@value MAX_REPAIR_ROUNDS} 轮。 */
    private ConversionResult selfHealing(Screenplay screenplay) {
        ValidationReport report = sceneValidator.validate(screenplay);
        log.info("首次体检：发现 {} 处问题", report.count());

        int round = 0;
        while (!report.isClean() && round < MAX_REPAIR_ROUNDS) {
            round++;
            screenplay = repairService.repair(screenplay, report);
            report = sceneValidator.validate(screenplay);
            log.info("第 {} 轮修复后：残留 {} 处问题", round, report.count());
        }
        return new ConversionResult(screenplay, report);
    }

    /** 块边界去重：去掉来源原文片段(excerpt)完全相同的重复场景，保留首次出现。 */
    private List<Scene> dedupByExcerpt(List<Scene> scenes) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<Scene> unique = new ArrayList<>(scenes.size());
        for (Scene scene : scenes) {
            String excerpt = (scene.source() == null) ? null : scene.source().excerpt();
            if (excerpt == null || excerpt.isBlank() || seen.add(excerpt.strip())) {
                unique.add(scene);
            } else {
                log.info("去除重复场景（来源片段相同）：{}", excerpt.strip());
            }
        }
        return unique;
    }

    /**
     * 拼装注入抽取 prompt 的"前情提要" = 跨章前情(recap) + 本章已抽场景的 summary。
     * 复用已有产物、不额外调模型；只保留最近 {@value SYNOPSIS_MAX_CHARS} 字，控制 token。
     */
    private String composeSynopsis(String recap, List<Scene> scenesSoFar) {
        StringBuilder sb = new StringBuilder();
        if (recap != null && !recap.isBlank()) {
            sb.append(recap.strip()).append(' ');
        }
        for (Scene scene : scenesSoFar) {
            if (scene.summary() != null && !scene.summary().isBlank()) {
                sb.append(scene.summary().strip()).append(' ');
            }
        }
        return tail(sb.toString().strip(), SYNOPSIS_MAX_CHARS);
    }

    /** 超长则只保留末尾 max 个字符（最近剧情），否则原样返回。 */
    private String tail(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(s.length() - max);
    }
}
