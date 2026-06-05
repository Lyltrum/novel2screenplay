package com.novel2screenplay.model;

/**
 * 场景标题（slugline）：内外景 + 地点 + 时间。
 * 对标行业剧本「INT. 客栈大堂 - 夜」的三要素结构，是场景切分（按地点/时间）的落点。
 */
public record Heading(
        IntExt intExt,
        String location,
        String timeOfDay
) {
}
