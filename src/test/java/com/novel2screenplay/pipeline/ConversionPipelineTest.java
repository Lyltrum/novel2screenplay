package com.novel2screenplay.pipeline;

import com.novel2screenplay.assemble.ScreenplayAssembler;
import com.novel2screenplay.bible.StoryBibleService;
import com.novel2screenplay.episode.EpisodePlanner;
import com.novel2screenplay.episode.EpisodeValidator;
import com.novel2screenplay.extract.ChapterSynopsisService;
import com.novel2screenplay.extract.SceneExtractionService;
import com.novel2screenplay.extract.TitleLoglineService;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.SceneFunction;
import com.novel2screenplay.model.SourceRef;
import com.novel2screenplay.model.StoryBible;
import com.novel2screenplay.model.extraction.TitleLogline;
import com.novel2screenplay.split.ChapterSplitter;
import com.novel2screenplay.split.ChunkSplitter;
import com.novel2screenplay.style.StyleTemplate;
import com.novel2screenplay.validate.RepairService;
import com.novel2screenplay.validate.SceneValidator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 离线验证流水线的失败兜底——真·纯逻辑组件 + mock 模型服务，不需要 API key。 */
class ConversionPipelineTest {

    private final StoryBibleService bible = mock(StoryBibleService.class);
    private final ChapterSynopsisService synopsis = mock(ChapterSynopsisService.class);
    private final SceneExtractionService extract = mock(SceneExtractionService.class);
    private final TitleLoglineService titleLogline = mock(TitleLoglineService.class);
    private final RepairService repair = mock(RepairService.class);
    private final EpisodePlanner episodePlanner = mock(EpisodePlanner.class);

    private final ConversionPipeline pipeline = new ConversionPipeline(
            new ChapterSplitter(), new ChunkSplitter(1500), bible, synopsis, extract, titleLogline,
            new ScreenplayAssembler(), new SceneValidator(), repair,
            new StyleTemplate(), episodePlanner, new EpisodeValidator());

    private static final String NOVEL = "第一章 雨夜\n沈砚独坐。\n\n第二章 借座\n苏窈到来。";

    @Test
    void skipsFailedChunkButKeepsScenesFromOthers() {
        when(bible.extractDelta(any())).thenReturn(StoryBible.empty());
        when(bible.merge(any(), any())).thenReturn(StoryBible.empty());
        when(synopsis.summarize(any())).thenReturn("梗概");
        // 一章抽取抛错、另一章成功 → 整本不崩，保留成功章场景（重排为 S1）；并行下哪章失败不定，但成功数恒为 1
        when(extract.extract(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("模型抽风"))
                .thenReturn(List.of(scene()));
        when(titleLogline.generate(any(), any())).thenReturn(new TitleLogline("剧名", "梗概"));
        when(repair.repair(any(), any())).thenAnswer(i -> i.getArgument(0));

        ConversionResult result = pipeline.convert(NOVEL, "电影", null);

        assertThat(result.screenplay().scenes()).hasSize(1);
        assertThat(result.screenplay().scenes().get(0).id()).isEqualTo("S1");
    }

    @Test
    void throwsConversionExceptionWhenAllExtractionFails() {
        when(bible.extractDelta(any())).thenReturn(StoryBible.empty());
        when(bible.merge(any(), any())).thenReturn(StoryBible.empty());
        when(synopsis.summarize(any())).thenReturn("梗概");
        when(extract.extract(any(), any(), any(), any())).thenThrow(new RuntimeException("全挂"));

        assertThatThrownBy(() -> pipeline.convert(NOVEL, "电影", null))
                .isInstanceOf(ConversionException.class);
    }

    private Scene scene() {
        return new Scene(null,
                new Heading(IntExt.INT, "客栈", "夜晚"),
                "概要", List.of("油灯昏黄。"),
                List.of(new DialogueLine("沈砚", "", "请便。", null)),
                "", new SourceRef(2, "原文片段"),
                new SceneCraft("观察", "戒备", "破冰", SceneFunction.REVEAL_CHARACTER));
    }
}
