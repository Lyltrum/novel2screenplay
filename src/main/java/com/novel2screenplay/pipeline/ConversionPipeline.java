package com.novel2screenplay.pipeline;

import com.novel2screenplay.assemble.ScreenplayAssembler;
import com.novel2screenplay.bible.StoryBibleService;
import com.novel2screenplay.extract.SceneExtractionService;
import com.novel2screenplay.extract.TitleLoglineService;
import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.StoryBible;
import com.novel2screenplay.model.extraction.TitleLogline;
import com.novel2screenplay.split.ChapterSplitter;
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
    private static final String DEFAULT_STYLE = "电影";
    /** 自检修复闭环的最大轮数，避免无界循环与 token 浪费。 */
    private static final int MAX_REPAIR_ROUNDS = 2;

    private final ChapterSplitter chapterSplitter;
    private final StoryBibleService storyBibleService;
    private final SceneExtractionService sceneExtractionService;
    private final TitleLoglineService titleLoglineService;
    private final ScreenplayAssembler screenplayAssembler;
    private final SceneValidator sceneValidator;
    private final RepairService repairService;

    public ConversionPipeline(ChapterSplitter chapterSplitter,
                              StoryBibleService storyBibleService,
                              SceneExtractionService sceneExtractionService,
                              TitleLoglineService titleLoglineService,
                              ScreenplayAssembler screenplayAssembler,
                              SceneValidator sceneValidator,
                              RepairService repairService) {
        this.chapterSplitter = chapterSplitter;
        this.storyBibleService = storyBibleService;
        this.sceneExtractionService = sceneExtractionService;
        this.titleLoglineService = titleLoglineService;
        this.screenplayAssembler = screenplayAssembler;
        this.sceneValidator = sceneValidator;
        this.repairService = repairService;
    }

    public ConversionResult convert(String novelText, String style) {
        String effectiveStyle = (style == null || style.isBlank()) ? DEFAULT_STYLE : style.strip();

        List<Chapter> chapters = chapterSplitter.split(novelText);
        log.info("切分出 {} 章", chapters.size());

        StoryBible bible = StoryBible.empty();
        for (Chapter chapter : chapters) {
            bible = storyBibleService.update(bible, chapter);
        }
        log.info("登记表建立完成：人物 {} 位，地点 {} 处",
                bible.characters().size(), bible.locations().size());

        List<Scene> scenes = new ArrayList<>();
        for (Chapter chapter : chapters) {
            List<Scene> chapterScenes = sceneExtractionService.extract(chapter, bible, effectiveStyle);
            log.info("第 {} 章抽出 {} 个场景", chapter.index(), chapterScenes.size());
            scenes.addAll(chapterScenes);
        }

        TitleLogline titleLogline = titleLoglineService.generate(scenes, effectiveStyle);

        Screenplay screenplay = screenplayAssembler.assemble(
                titleLogline.title(),
                titleLogline.logline(),
                effectiveStyle,
                bible.characters(),
                scenes);

        return selfHealing(screenplay);
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
}
