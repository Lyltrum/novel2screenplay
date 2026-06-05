package com.novel2screenplay.model;

import java.util.List;

/**
 * 人物登记项：主名 + 别名/称呼 + 设定简述。
 * 抽成独立登记表（而非散落在各场景）是为了去重、跨场景一致、可复用。
 * aliases 收录同一角色的不同称呼（如「李白 / 太白 / 谪仙人」），是跨章人名归一的依据。
 */
public record Character(
        String name,
        List<String> aliases,
        String description
) {
}
