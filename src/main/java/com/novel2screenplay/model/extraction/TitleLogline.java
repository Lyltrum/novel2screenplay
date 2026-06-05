package com.novel2screenplay.model.extraction;

/**
 * 剧名/梗概生成调用的包装 record（仅内部使用）。
 */
public record TitleLogline(
        String title,
        String logline
) {
}
