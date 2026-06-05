package com.novel2screenplay.model;

import java.util.List;

/**
 * 跨章一致性登记表（仅内部使用，不进最终 YAML）。
 * 逐章累积更新人物表与地点表，再注入每章的场景抽取 prompt，
 * 以保证跨章人名/称呼/设定一致——这是质量分的命门。
 */
public record StoryBible(
        List<Character> characters,
        List<String> locations
) {
    /** 处理首章前的空登记表。 */
    public static StoryBible empty() {
        return new StoryBible(List.of(), List.of());
    }
}
