package com.novel2screenplay.model;

import java.util.List;

/**
 * 剧集中的一「集」（电视剧/网剧的分集结构）。
 * 集是叠加在场景之上的编排层：不持有场景内容，只通过 sceneIds 引用本集包含的场景。
 * 这样 scenes 仍是全局扁平的"素材库"，episodes 只是"如何分集"的编排，互不破坏。
 * <p>
 * 集是电视剧剧本的一级创作单元（集≠小说的章）：编剧先定分集大纲，每集有独立起承转合 + 集尾钩子。
 *
 * @param number   集号，从 1 递增
 * @param title    集名
 * @param synopsis 本集一句话梗概
 * @param hook     集尾钩子（悬念/转折/危机），驱动观众追看下一集
 * @param sceneIds 本集包含的场景 id 列表（引用 Screenplay.scenes 中的 id，按播出顺序）
 */
public record Episode(
        int number,
        String title,
        String synopsis,
        String hook,
        List<String> sceneIds
) {
}
