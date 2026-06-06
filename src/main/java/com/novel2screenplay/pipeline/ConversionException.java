package com.novel2screenplay.pipeline;

/**
 * 转换"尽力而为"后仍无法产出有效剧本时抛出（如模型调用全部失败、抽不出任何场景）。
 * 定义在 pipeline 包、为领域异常，由 Web 层的全局异常处理映射为干净的错误响应（502），
 * 这样 pipeline 不必依赖 Web 层类型。
 */
public class ConversionException extends RuntimeException {
    public ConversionException(String message) {
        super(message);
    }
}
