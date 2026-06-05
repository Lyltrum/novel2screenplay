package com.novel2screenplay.model;

import java.util.List;

/**
 * 顶层剧本：剧名 + 一句话梗概 + 改编风格 + 全局人物表 + 场景表。
 * 这是整个 Schema 的单一事实源——record 即 Schema，YAML 输出与设计文档都以此为准。
 * style：改编风格（电影/话剧/短剧/分镜），亮点 P3。
 */
public record Screenplay(
        String title,
        String logline,
        String style,
        List<Character> characters,
        List<Scene> scenes
) {
}
