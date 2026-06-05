package com.novel2screenplay.refine;

import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.prompt.Prompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 只负责：按作者指令精修单个场景（交互式精修）。
 * 把当前场景序列化成 YAML 作为上下文，连同人物登记表与修改指令喂给模型，
 * 返回精修后的场景；场景 id 与来源出处由程序保留，保证可追溯性不丢。
 */
@Service
public class SceneRefinementService {

    private final ChatClient chatClient;
    private final YamlExporter yamlExporter;

    public SceneRefinementService(ChatClient chatClient, YamlExporter yamlExporter) {
        this.chatClient = chatClient;
        this.yamlExporter = yamlExporter;
    }

    public Scene refine(Scene scene, List<Character> characters, String instruction) {
        String sceneYaml = yamlExporter.toYaml(
                new Screenplay(null, null, null, List.of(), List.of(scene)));

        Scene refined = chatClient.prompt()
                .user(Prompts.refine(instruction, characters, sceneYaml))
                .call()
                .entity(Scene.class);

        // 保留原 id 与来源出处（精修不改变身份与溯源）
        return new Scene(
                scene.id(),
                refined.heading(),
                refined.summary(),
                refined.action(),
                refined.dialogue(),
                refined.transition(),
                scene.source());
    }
}
