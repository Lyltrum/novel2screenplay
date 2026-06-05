package com.novel2screenplay.model;

/**
 * 来源可追溯（亮点 P1）：本场景改编自原文的哪一章、哪段原文。
 * 让 AI 生成的剧本不再是黑盒——作者可逐场景对照原文校验改编是否忠实。
 * chapter 为来源章节号（从 1 计）；excerpt 为触发本场景的原文片段引用。
 */
public record SourceRef(
        int chapter,
        String excerpt
) {
}
