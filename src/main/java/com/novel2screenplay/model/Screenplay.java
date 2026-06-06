package com.novel2screenplay.model;

import java.util.List;

/**
 * 顶层剧本：溯源信息 + 剧名 + 一句话梗概 + 改编风格 + 全局人物表 + 分集编排 + 场景表。
 * 这是整个 Schema 的单一事实源——record 即 Schema，YAML 输出与设计文档都以此为准。
 * meta：溯源信息（改编自几章、共几场），由流水线计算填充，可空。
 * style：改编风格——默认主流为「剧集」（电视剧/网剧）；电影/话剧/短剧/分镜为可选单本形态。
 * episodes：分集编排层（叠加在 scenes 之上，通过 sceneIds 引用）；仅剧集形态填充，单本形态为空。
 */
public record Screenplay(
        ScreenplayMeta meta,
        String title,
        String logline,
        String style,
        List<Character> characters,
        List<Episode> episodes,
        List<Scene> scenes
) {
}
