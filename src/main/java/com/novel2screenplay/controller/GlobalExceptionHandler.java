package com.novel2screenplay.controller;

import com.novel2screenplay.pipeline.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 只负责：把各类异常转成干净的 JSON 错误响应（{status, error}），不把堆栈直接吐给调用方；
 * 服务端仍记录完整错误，保证"失败可观测"又不影响 demo 体验。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 控制器主动抛出的入参错误（如空文本/空文件），保留其状态码与原因。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleStatus(ResponseStatusException e) {
        return body(HttpStatus.valueOf(e.getStatusCode().value()), e.getReason());
    }

    /** 上传文件超过 multipart 大小限制。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleTooLarge(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "上传文件过大，请压缩或拆分后重试");
    }

    /** 请求体缺失/无法解析（如空正文），或 multipart 缺少 file 字段——都属客户端入参问题，返回 400。 */
    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return body(HttpStatus.BAD_REQUEST, "请求体为空或格式不正确：请在正文放入小说文本，或用 file 字段上传 .txt");
    }

    /** 转换尽力而为后仍无有效产出（如模型调用全部失败）。 */
    @ExceptionHandler(ConversionException.class)
    public ResponseEntity<Map<String, Object>> handleConversion(ConversionException e) {
        log.warn("转换未产出有效剧本：{}", e.getMessage());
        return body(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    /** 兜底：其余未预期异常返回 500（不外泄堆栈），完整错误记入服务端日志。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("请求处理失败", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误：" + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", status.value());
        m.put("error", message == null ? status.getReasonPhrase() : message);
        return ResponseEntity.status(status).body(m);
    }
}
