package com.novel2screenplay.validate;

import java.util.List;

/**
 * 一次校验的汇总结果（仅内部使用）。
 */
public record ValidationReport(
        List<ValidationIssue> issues
) {
    public boolean isClean() {
        return issues.isEmpty();
    }

    public int count() {
        return issues.size();
    }
}
