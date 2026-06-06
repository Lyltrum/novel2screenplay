package com.novel2screenplay.controller;

import com.novel2screenplay.export.FountainExporter;
import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.SceneFunction;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.ScreenplayMeta;
import com.novel2screenplay.model.SourceRef;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.pipeline.ConversionPipeline;
import com.novel2screenplay.pipeline.ConversionResult;
import com.novel2screenplay.refine.SceneRefinementService;
import com.novel2screenplay.validate.SceneValidator;
import com.novel2screenplay.validate.ValidationIssue;
import com.novel2screenplay.validate.ValidationReport;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 离线验证控制器逻辑——用 Mockito 假 pipeline/精修服务 + 真导出器与校验器，不需要 API key。 */
class ScreenplayControllerTest {

    private final ConversionPipeline pipeline = mock(ConversionPipeline.class);
    private final SceneRefinementService refinementService = mock(SceneRefinementService.class);
    private final ScreenplayController controller =
            new ScreenplayController(pipeline, new YamlExporter(), new FountainExporter(),
                    refinementService, new SceneValidator());

    @Test
    void returnsYamlByDefaultWithWarningHeader() {
        when(pipeline.convert("小说正文", "电影", null)).thenReturn(sampleResult());

        ResponseEntity<String> resp = controller.convert("小说正文", null, "电影", "yaml", null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("int_ext:");
        assertThat(resp.getHeaders().getContentType().toString()).contains("yaml");
        assertThat(resp.getHeaders().getFirst("X-Validation-Warnings")).isEqualTo("1");
    }

    @Test
    void returnsFountainWhenRequested() {
        when(pipeline.convert("小说正文", "电影", null)).thenReturn(sampleResult());

        ResponseEntity<String> resp = controller.convert("小说正文", null, "电影", "fountain", null);

        assertThat(resp.getBody()).contains("INT. 客栈 - 黄昏");
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/plain");
    }

    @Test
    void overridesTitleWhenProvided() {
        when(pipeline.convert("小说正文", "电影", null)).thenReturn(sampleResult());

        ResponseEntity<String> resp = controller.convert("小说正文", "我的剧名", "电影", "yaml", null);

        assertThat(resp.getBody()).contains("title: 我的剧名");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> controller.convert("   ", null, "电影", "yaml", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void convertFileReadsUploadedNovelAndSetsDownloadHeader() {
        when(pipeline.convert("小说正文", "电影", null)).thenReturn(sampleResult());
        MockMultipartFile file = new MockMultipartFile("file", "novel.txt", "text/plain",
                "小说正文".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<String> resp = controller.convertFile(file, null, "电影", "yaml", null);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("int_ext:");
        assertThat(resp.getHeaders().getFirst("X-Validation-Warnings")).isEqualTo("1");
        // 输出文件：Content-Disposition 以剧名作下载文件名
        assertThat(resp.getHeaders().getFirst("Content-Disposition")).contains("filename");
    }

    @Test
    void convertFileRejectsEmptyUpload() {
        MockMultipartFile empty = new MockMultipartFile("file", "novel.txt", "text/plain", new byte[0]);
        assertThatThrownBy(() -> controller.convertFile(empty, null, "电影", "yaml", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void refineReturnsRefinedSceneWithWarningHeader() {
        Character shen = new Character("沈砚", List.of(), "剑客");
        Scene refined = new Scene("S1",
                new Heading(IntExt.EXT, "后巷", "午夜"),
                "精修后的概要",
                List.of("黑影翻墙，沈砚疾追。"),
                List.of(new DialogueLine("沈砚", "(低喝)", "站住！", null)),
                "", new SourceRef(2, "原文片段"),
                new SceneCraft("拦下黑影", "对方夺路而逃", "沈砚追入窄巷，对峙在即", SceneFunction.ESCALATE_CONFLICT));
        when(refinementService.refine(any(), eq(List.of(shen)), eq("让动作更紧张")))
                .thenReturn(refined);

        RefineRequest req = new RefineRequest(refined, List.of(shen), "让动作更紧张");
        ResponseEntity<Scene> resp = controller.refine(req);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().id()).isEqualTo("S1");
        assertThat(resp.getBody().summary()).isEqualTo("精修后的概要");
        // 该场景合法 → 残留 0
        assertThat(resp.getHeaders().getFirst("X-Validation-Warnings")).isEqualTo("0");
    }

    @Test
    void refineRejectsMissingScene() {
        assertThatThrownBy(() -> controller.refine(new RefineRequest(null, List.of(), "改一下")))
                .isInstanceOf(ResponseStatusException.class);
    }

    private ConversionResult sampleResult() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "客栈", "黄昏"),
                "概要", List.of("油灯昏黄。"), List.of(), "",
                new SourceRef(1, "原文片段"), null);
        Screenplay sp = new Screenplay(new ScreenplayMeta(3, 1), "自动剧名", "梗概", "电影", List.of(), null, List.of(scene));
        ValidationReport report = new ValidationReport(
                List.of(new ValidationIssue("S1", "x", "残留问题")));
        return new ConversionResult(sp, report);
    }
}
