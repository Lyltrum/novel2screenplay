package com.novel2screenplay.extract;

import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.extraction.TitleLogline;
import com.novel2screenplay.prompt.Prompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 只负责：根据各场景概要生成剧名与一句话梗概。
 * 仅传场景 summary 而非全文，控制 token 成本。
 */
@Service
public class TitleLoglineService {

    private final ChatClient chatClient;

    public TitleLoglineService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public TitleLogline generate(List<Scene> scenes, String style) {
        String synopsis = scenes.stream()
                .map(Scene::summary)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining("\n"));

        return chatClient.prompt()
                .user(Prompts.titleLogline(synopsis, style))
                .call()
                .entity(TitleLogline.class);
    }
}
