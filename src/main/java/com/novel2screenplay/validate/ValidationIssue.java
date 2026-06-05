package com.novel2screenplay.validate;

/**
 * 一条校验问题（仅内部使用，不进最终 YAML）。
 * sceneId：问题所在场景（顶层问题用 "-"）。
 * field：出问题的字段。
 * message：人类可读的问题描述，同时用于喂给模型做修复。
 */
public record ValidationIssue(
        String sceneId,
        String field,
        String message
) {
    @Override
    public String toString() {
        return "[" + sceneId + "] " + field + "：" + message;
    }
}
