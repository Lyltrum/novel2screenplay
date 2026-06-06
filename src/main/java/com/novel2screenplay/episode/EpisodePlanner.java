package com.novel2screenplay.episode;

import com.novel2screenplay.model.Episode;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.extraction.EpisodePlan;
import com.novel2screenplay.prompt.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 只负责：把已分好场景的剧本，按戏剧节奏重新编排成「集」（剧集形态）。
 * 模拟编剧写"分集大纲"——输入场景精简清单，输出分集方案；只引用场景 id、不改写场景内容。
 * <p>
 * 长篇适配：场景数超过单次上限时按顺序分窗，逐窗分集再拼接重排集号——
 * 窗内"不重不漏" ⟹ 全局"不重不漏"，且单次调用的清单与输出都有界，不撞上下文/输出上限。
 * 代价：集边界对齐窗边界（一集不跨窗），全局节奏为局部最优，可接受。
 */
@Service
public class EpisodePlanner {

    private static final Logger log = LoggerFactory.getLogger(EpisodePlanner.class);

    private final ChatClient chatClient;
    /** 单次分集调用最多容纳的场景数；超过则分窗规划。 */
    private final int maxScenesPerPlan;

    public EpisodePlanner(ChatClient chatClient,
                          @Value("${screenplay.episode.max-scenes-per-plan:80}") int maxScenesPerPlan) {
        this.chatClient = chatClient;
        this.maxScenesPerPlan = maxScenesPerPlan;
    }

    /** 分集：targetEpisodes 为 null 或 ≤0 时，由模型按剧情节奏自动决定集数。 */
    public List<Episode> plan(Screenplay screenplay, Integer targetEpisodes) {
        List<Scene> scenes = screenplay.scenes();
        if (scenes == null || scenes.isEmpty()) {
            return List.of();
        }
        if (scenes.size() <= maxScenesPerPlan) {
            return planWindow(scenes, targetEpisodes);
        }

        List<List<Scene>> windows = splitIntoWindows(scenes, maxScenesPerPlan);
        log.info("场景数 {} 超过单次上限 {}，分 {} 窗规划分集", scenes.size(), maxScenesPerPlan, windows.size());
        List<Episode> all = new ArrayList<>();
        for (List<Scene> window : windows) {
            all.addAll(planWindow(window, perWindowTarget(targetEpisodes, window.size(), scenes.size())));
        }
        return renumber(all);
    }

    /** 单次分集调用：把一批场景的精简清单交给模型，产出该批的集编排。 */
    private List<Episode> planWindow(List<Scene> scenes, Integer targetEpisodes) {
        EpisodePlan plan = chatClient.prompt()
                .user(Prompts.episodePlanning(renderSceneList(scenes), targetEpisodes))
                .call()
                .entity(EpisodePlan.class);
        if (plan == null || plan.episodes() == null) {
            return List.of();
        }
        return plan.episodes();
    }

    /** 按场景顺序切成若干 ≤ size 的连续窗口（纯逻辑，便于测试）。 */
    static List<List<Scene>> splitIntoWindows(List<Scene> scenes, int size) {
        List<List<Scene>> windows = new ArrayList<>();
        for (int i = 0; i < scenes.size(); i += size) {
            windows.add(new ArrayList<>(scenes.subList(i, Math.min(i + size, scenes.size()))));
        }
        return windows;
    }

    /** 指定总集数时按窗内场景占比分摊（至少 1 集）；未指定则返回 null 交模型决定。 */
    private Integer perWindowTarget(Integer total, int windowScenes, int totalScenes) {
        if (total == null || total <= 0) {
            return null;
        }
        return Math.max(1, Math.round((float) total * windowScenes / totalScenes));
    }

    /** 跨窗拼接后按顺序重排集号 1..N（纯逻辑，便于测试）。 */
    static List<Episode> renumber(List<Episode> episodes) {
        List<Episode> out = new ArrayList<>(episodes.size());
        int n = 1;
        for (Episode ep : episodes) {
            out.add(new Episode(n++, ep.title(), ep.synopsis(), ep.hook(), ep.sceneIds()));
        }
        return out;
    }

    /** 场景精简清单：id | 概要 | 本场转折 | 戏剧职能——足够模型判断分集节奏，又控制 token。 */
    private String renderSceneList(List<Scene> scenes) {
        StringBuilder sb = new StringBuilder();
        for (Scene s : scenes) {
            SceneCraft craft = s.craft();
            String turn = (craft != null && craft.turn() != null) ? craft.turn().strip() : "";
            String function = (craft != null && craft.function() != null) ? craft.function().name() : "";
            sb.append(nullToEmpty(s.id()))
                    .append(" | ").append(nullToEmpty(s.summary()))
                    .append(" | ").append(turn)
                    .append(" | ").append(function)
                    .append('\n');
        }
        return sb.toString().strip();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s.strip();
    }
}
