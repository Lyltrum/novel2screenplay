package com.novel2screenplay.controller;

import com.novel2screenplay.pipeline.ConversionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证全局异常处理——把异常映射成干净的 {status, error} 响应，不外泄堆栈。 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsResponseStatusExceptionToItsStatusAndReason() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleStatus(new ResponseStatusException(HttpStatus.BAD_REQUEST, "小说文本不能为空"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody()).containsEntry("status", 400);
        assertThat(resp.getBody().get("error")).isEqualTo("小说文本不能为空");
    }

    @Test
    void mapsConversionExceptionToBadGateway() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleConversion(new ConversionException("未能抽取出任何场景"));

        assertThat(resp.getStatusCode().value()).isEqualTo(502);
        assertThat(resp.getBody().get("error")).isEqualTo("未能抽取出任何场景");
    }

    @Test
    void mapsUnreadableBodyTo400() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleBadRequest(new HttpMessageNotReadableException("Required request body is missing"));

        assertThat(resp.getStatusCode().value()).isEqualTo(400);
        assertThat(resp.getBody().get("error").toString()).contains("请求体");
    }

    @Test
    void mapsUploadTooLargeTo413() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleTooLarge(new MaxUploadSizeExceededException(20L));

        assertThat(resp.getStatusCode().value()).isEqualTo(413);
    }

    @Test
    void mapsUnexpectedExceptionTo500WithoutLeakingStack() {
        ResponseEntity<Map<String, Object>> resp =
                handler.handleOther(new RuntimeException("boom"));

        assertThat(resp.getStatusCode().value()).isEqualTo(500);
        assertThat(resp.getBody().get("error").toString()).contains("服务器内部错误");
    }
}
