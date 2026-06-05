package com.novel2screenplay.controller;

import com.novel2screenplay.export.FountainExporter;
import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.SourceRef;
import com.novel2screenplay.pipeline.ConversionPipeline;
import com.novel2screenplay.pipeline.ConversionResult;
import com.novel2screenplay.validate.ValidationIssue;
import com.novel2screenplay.validate.ValidationReport;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 离线验证控制器逻辑——用 Mockito 假 pipeline + 真导出器，不需要 API key。 */
class ScreenplayControllerTest {

    private final ConversionPipeline pipeline = mock(ConversionPipeline.class);
    private final ScreenplayController controller =
            new ScreenplayController(pipeline, new YamlExporter(), new FountainExporter());

    @Test
    void returnsYamlByDefaultWithWarningHeader() {
        when(pipeline.convert("小说正文", "电影")).thenReturn(sampleResult());

        ResponseEntity<String> resp = controller.convert("小说正文", null, "电影", "yaml");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).contains("int_ext:");
        assertThat(resp.getHeaders().getContentType().toString()).contains("yaml");
        assertThat(resp.getHeaders().getFirst("X-Validation-Warnings")).isEqualTo("1");
    }

    @Test
    void returnsFountainWhenRequested() {
        when(pipeline.convert("小说正文", "电影")).thenReturn(sampleResult());

        ResponseEntity<String> resp = controller.convert("小说正文", null, "电影", "fountain");

        assertThat(resp.getBody()).contains("INT. 客栈 - 黄昏");
        assertThat(resp.getHeaders().getContentType().toString()).contains("text/plain");
    }

    @Test
    void overridesTitleWhenProvided() {
        when(pipeline.convert("小说正文", "电影")).thenReturn(sampleResult());

        ResponseEntity<String> resp = controller.convert("小说正文", "我的剧名", "电影", "yaml");

        assertThat(resp.getBody()).contains("title: 我的剧名");
    }

    @Test
    void rejectsBlankInput() {
        assertThatThrownBy(() -> controller.convert("   ", null, "电影", "yaml"))
                .isInstanceOf(ResponseStatusException.class);
    }

    private ConversionResult sampleResult() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "客栈", "黄昏"),
                "概要", List.of("油灯昏黄。"), List.of(), "",
                new SourceRef(1, "原文片段"));
        Screenplay sp = new Screenplay("自动剧名", "梗概", "电影", List.of(), List.of(scene));
        ValidationReport report = new ValidationReport(
                List.of(new ValidationIssue("S1", "x", "残留问题")));
        return new ConversionResult(sp, report);
    }
}
