package com.novel2screenplay.validate;

import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.prompt.Prompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

/**
 * 只负责：带着体检问题清单，让模型把剧本修一遍（自检修复闭环的"修"）。
 * 把当前剧本序列化成 YAML 作为上下文喂给模型，要求其逐条修复、其余不动。
 */
@Service
public class RepairService {

    private final ChatClient chatClient;
    private final YamlExporter yamlExporter;

    public RepairService(ChatClient chatClient, YamlExporter yamlExporter) {
        this.chatClient = chatClient;
        this.yamlExporter = yamlExporter;
    }

    public Screenplay repair(Screenplay current, ValidationReport report) {
        String issues = report.issues().stream()
                .map(ValidationIssue::toString)
                .collect(Collectors.joining("\n"));
        String currentYaml = yamlExporter.toYaml(current);

        return chatClient.prompt()
                .user(Prompts.repair(issues, currentYaml))
                .call()
                .entity(Screenplay.class);
    }
}
