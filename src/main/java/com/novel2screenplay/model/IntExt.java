package com.novel2screenplay.model;

/**
 * 内景 / 外景 / 内外景，对标剧本 slugline 的 INT. / EXT. 标记。
 * 用枚举而非自由字符串，保证场景标题的内外景取值受控、可校验。
 */
public enum IntExt {
    INT,
    EXT,
    INT_EXT
}
