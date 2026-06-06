package com.novel2screenplay.model.extraction;

import com.novel2screenplay.model.Episode;

import java.util.List;

/**
 * 仅内部使用：分集服务（EpisodePlanner）的模型结构化输出包装。
 * 模型读完整场景清单后，模拟编剧写"分集大纲"，返回若干集的编排方案。
 * 不进入最终 YAML——最终 YAML 里只有 Screenplay.episodes（List&lt;Episode&gt;）。
 */
public record EpisodePlan(
        List<Episode> episodes
) {
}
