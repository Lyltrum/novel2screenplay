package com.novel2screenplay.pipeline;

import com.novel2screenplay.assemble.ScreenplayAssembler;
import com.novel2screenplay.episode.EpisodePlanner;
import com.novel2screenplay.episode.EpisodeValidator;
import com.novel2screenplay.model.Episode;
import com.novel2screenplay.style.StyleTemplate;
import com.novel2screenplay.validate.ValidationIssue;
import com.novel2screenplay.bible.StoryBibleService;
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

/**
 * 只负责：编排小说→剧本的完整流水线（各步骤的调用顺序），自身不含具体抽取逻辑。
 * <p>
 * 顺序：切章 → 逐章登记建立完整 StoryBible → 注入完整登记表逐章抽场景
 *      → 生成剧名/梗概 → 组装并全局编号。
 * 先建完整登记表再抽场景，可让靠前章节也"知道"靠后章节才出现的称呼，跨章一致性最强。
 */
@Component
public class ConversionPipeline {

    private static final Logger log = LoggerFactory.getLogger(ConversionPipeline.class);
    private static final String DEFAULT_STYLE = "剧集";
    /** 自检修复闭环的最大轮数，避免无界循环与 token 浪费。 */
    private static final int MAX_REPAIR_ROUNDS = 2;
    /** 滚动提要保留的最大字符数（取最近剧情，控制 token）。 */
    private static final int SYNOPSIS_MAX_CHARS = 800;

    private final ChapterSplitter chapterSplitter;
    private final ChunkSplitter chunkSplitter;
    private final StoryBibleService storyBibleService;
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
        this.sceneExtractionService = sceneExtractionService;
        this.titleLoglineService = titleLoglineService;
        this.screenplayAssembler = screenplayAssembler;
        this.sceneValidator = sceneValidator;
        this.repairService = repairService;
        this.styleTemplate = styleTemplate;
        this.episodePlanner = episodePlanner;
        this.episodeValidator = episodeValidator;
    }

    public ConversionResult convert(String novelText, String style, Integer episodes) {
        String effectiveStyle = (style == null || style.isBlank()) ? DEFAULT_STYLE : style.strip();

        List<Chapter> chapters = chapterSplitter.split(novelText);
        log.info("切分出 {} 章", chapters.size());

        StoryBible bible = StoryBible.empty();
        for (Chapter chapter : chapters) {
            bible = storyBibleService.update(bible, chapter);
        }
        log.info("登记表建立完成：人物 {} 位，地点 {} 处",
                bible.characters().size(), bible.locations().size());

        // 逐章 → 逐块抽取（长文规模化）：超长章节切块，块间用滚动提要保叙事连续
        List<Scene> scenes = new ArrayList<>();
        for (Chapter chapter : chapters) {
            List<String> chunks = chunkSplitter.split(chapter.text());
            int chapterSceneCount = 0;
            for (String chunk : chunks) {
                Chapter chunkUnit = new Chapter(chapter.index(), chapter.title(), chunk);
                String synopsis = buildSynopsis(scenes);
                List<Scene> chunkScenes =
                        sceneExtractionService.extract(chunkUnit, bible, effectiveStyle, synopsis);
                scenes.addAll(chunkScenes);
                chapterSceneCount += chunkScenes.size();
            }
            log.info("第 {} 章（{} 块）抽出 {} 个场景", chapter.index(), chunks.size(), chapterSceneCount);
        }

        scenes = dedupByExcerpt(scenes);

        TitleLogline titleLogline = titleLoglineService.generate(scenes, effectiveStyle);

        Screenplay screenplay = screenplayAssembler.assemble(
                chapters.size(),
                titleLogline.title(),
                titleLogline.logline(),
                effectiveStyle,
                bible.characters(),
                scenes);

        ConversionResult result = selfHealing(screenplay);

        // 剧集形态：在场景之上叠加"集"编排（分集大纲 + 集尾钩子）；单本形态(电影/话剧/短剧/分镜)跳过
        if (styleTemplate.isSeries(effectiveStyle)) {
            result = planEpisodes(result, episodes);
        }
        return result;
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
     * 用已抽取场景的 summary 拼成滚动提要，作为后续块/章的"前情提要"——
     * 复用已有产物，不额外调用模型；只保留最近 {@value SYNOPSIS_MAX_CHARS} 字，控制 token。
     */
    private String buildSynopsis(List<Scene> scenesSoFar) {
        if (scenesSoFar.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Scene scene : scenesSoFar) {
            if (scene.summary() != null && !scene.summary().isBlank()) {
                sb.append(scene.summary().strip()).append(' ');
            }
        }
        String synopsis = sb.toString().strip();
        if (synopsis.length() > SYNOPSIS_MAX_CHARS) {
            synopsis = synopsis.substring(synopsis.length() - SYNOPSIS_MAX_CHARS);
        }
        return synopsis;
    }
}
