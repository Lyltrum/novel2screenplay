package com.novel2screenplay.pipeline;

import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.validate.ValidationReport;

/**
 * 流水线产物：最终剧本 + 修复闭环后残留的校验报告。
 * 残留问题数可暴露给调用方（如 REST 的 X-Validation-Warnings 头），让作者知道哪些地方还需人工打磨。
 */
public record ConversionResult(
        Screenplay screenplay,
        ValidationReport report
) {
}
