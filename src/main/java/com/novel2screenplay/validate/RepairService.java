package com.novel2screenplay.validate;

import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.prompt.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 只负责：带着体检问题清单，让模型把剧本修一遍（自检修复闭环的"修"）。
 * <p>
 * 按【场景】逐个修复——只把有问题的场景连同它的问题清单喂给模型重生成，而非整本剧本进出。
 * 这样输入/输出都有界：长篇不会撞上模型单次输出上限，也不会因输出截断而丢场景；
 * 同时只重处理坏场景，省 token。来源 source 由程序保留不交模型改写，护住可追溯性
 * （缺失的 excerpt 无法凭空补，宁可留作残留告警也不让模型杜撰）。
 * 顶层问题(title/logline)极少见，本服务不处理，留作残留告警。
 */
@Service
public class RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairService.class);

    private final ChatClient chatClient;
    private final YamlExporter yamlExporter;

    public RepairService(ChatClient chatClient, YamlExporter yamlExporter) {
        this.chatClient = chatClient;
        this.yamlExporter = yamlExporter;
    }

    public Screenplay repair(Screenplay current, ValidationReport report) {
        Map<String, List<ValidationIssue>> bySceneId = groupBySceneId(report);
        if (bySceneId.isEmpty()) {
            return current;
        }
        List<Character> characters = current.characters();
        List<Scene> repaired = new ArrayList<>(current.scenes().size());
        for (Scene scene : current.scenes()) {
            List<ValidationIssue> sceneIssues = bySceneId.get(scene.id());
            repaired.add(sceneIssues == null ? scene : repairScene(scene, sceneIssues, characters));
        }
        return withScenes(current, repaired);
    }

    /** 修单个场景：只喂该场 YAML + 它的问题 + 人物表；保留 id 与来源出处。失败则保留原场景。 */
    private Scene repairScene(Scene scene, List<ValidationIssue> sceneIssues, List<Character> characters) {
        String issues = sceneIssues.stream()
                .map(ValidationIssue::toString)
                .collect(Collectors.joining("\n"));
        String sceneYaml = yamlExporter.toYaml(
                new Screenplay(null, null, null, null, List.of(), List.of(), List.of(scene)));
        try {
            Scene fixed = chatClient.prompt()
                    .user(Prompts.repairScene(issues, characters, sceneYaml))
                    .call()
                    .entity(Scene.class);
            return new Scene(scene.id(), fixed.heading(), fixed.summary(), fixed.action(),
                    fixed.dialogue(), fixed.transition(), scene.source(), fixed.craft());
        } catch (Exception e) {
            log.warn("场景 {} 修复失败，保留原场景：{}", scene.id(), e.getMessage());
            return scene;
        }
    }

    /** 按场景 id 归并问题；忽略顶层("-")问题。 */
    private Map<String, List<ValidationIssue>> groupBySceneId(ValidationReport report) {
        Map<String, List<ValidationIssue>> map = new LinkedHashMap<>();
        for (ValidationIssue issue : report.issues()) {
            if (issue.sceneId() == null || "-".equals(issue.sceneId())) {
                continue;
            }
            map.computeIfAbsent(issue.sceneId(), k -> new ArrayList<>()).add(issue);
        }
        return map;
    }

    private Screenplay withScenes(Screenplay sp, List<Scene> scenes) {
        return new Screenplay(sp.meta(), sp.title(), sp.logline(), sp.style(),
                sp.characters(), sp.episodes(), scenes);
    }
}
