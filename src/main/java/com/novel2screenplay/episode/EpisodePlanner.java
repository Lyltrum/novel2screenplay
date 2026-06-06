package com.novel2screenplay.episode;

import com.novel2screenplay.model.Episode;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.extraction.EpisodePlan;
import com.novel2screenplay.prompt.Prompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 只负责：把已分好场景的剧本，按戏剧节奏重新编排成「集」（剧集形态）。
 * 模拟编剧写"分集大纲"——一次模型调用，输入全部场景的精简清单，输出分集方案。
 * 只引用场景 id、不改写场景内容；分集是叠加层，scenes 保持不变。
 */
@Service
public class EpisodePlanner {

    private final ChatClient chatClient;

    public EpisodePlanner(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 分集：targetEpisodes 为 null 或 ≤0 时，由模型按剧情节奏自动决定集数。 */
    public List<Episode> plan(Screenplay screenplay, Integer targetEpisodes) {
        List<Scene> scenes = screenplay.scenes();
        if (scenes == null || scenes.isEmpty()) {
            return List.of();
        }
        EpisodePlan plan = chatClient.prompt()
                .user(Prompts.episodePlanning(renderSceneList(scenes), targetEpisodes))
                .call()
                .entity(EpisodePlan.class);
        if (plan == null || plan.episodes() == null) {
            return List.of();
        }
        return plan.episodes();
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
