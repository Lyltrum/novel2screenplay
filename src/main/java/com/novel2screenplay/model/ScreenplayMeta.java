package com.novel2screenplay.model;

/**
 * 剧本溯源信息（顶层 meta 块）：体现这份初稿"改编自多少章、切出多少场"的来历，
 * 便于作者评估规模与覆盖度。由流水线在组装阶段计算填充。
 */
public record ScreenplayMeta(
        int sourceChapters,
        int sceneCount
) {
}
