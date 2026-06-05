package com.novel2screenplay.validate;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 只负责：体检剧本，产出问题清单（不改数据）。
 * 修改交给 RepairService——校验与修复职责分离。
 * <p>
 * 检查项：必填字段、场景标题取值、time_of_day 须中文、对白角色须在登记表内、
 * 对白 line 非空、来源可追溯字段完整。
 */
@Component
public class SceneValidator {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    public ValidationReport validate(Screenplay screenplay) {
        List<ValidationIssue> issues = new ArrayList<>();

        if (isBlank(screenplay.title())) {
            issues.add(new ValidationIssue("-", "title", "剧名为空"));
        }
        if (isBlank(screenplay.logline())) {
            issues.add(new ValidationIssue("-", "logline", "一句话梗概为空"));
        }

        Set<String> known = knownCharacterNames(screenplay.characters());

        for (Scene scene : screenplay.scenes()) {
            String id = scene.id();
            validateHeading(id, scene.heading(), issues);
            validateContent(id, scene, issues);
            validateDialogue(id, scene.dialogue(), known, issues);
            validateSource(id, scene, issues);
        }
        return new ValidationReport(issues);
    }

    private void validateHeading(String id, Heading heading, List<ValidationIssue> issues) {
        if (heading == null) {
            issues.add(new ValidationIssue(id, "heading", "缺少场景标题"));
            return;
        }
        if (heading.intExt() == null) {
            issues.add(new ValidationIssue(id, "heading.int_ext", "缺少内外景标记"));
        }
        if (isBlank(heading.location())) {
            issues.add(new ValidationIssue(id, "heading.location", "缺少地点"));
        }
        String time = heading.timeOfDay();
        if (isBlank(time)) {
            issues.add(new ValidationIssue(id, "heading.time_of_day", "缺少时间"));
        } else if (!CJK.matcher(time).find()) {
            issues.add(new ValidationIssue(id, "heading.time_of_day",
                    "时间应使用中文（如 黄昏/夜晚/黎明），当前为「" + time + "」"));
        }
    }

    private void validateContent(String id, Scene scene, List<ValidationIssue> issues) {
        boolean noAction = scene.action() == null || scene.action().isEmpty();
        boolean noDialogue = scene.dialogue() == null || scene.dialogue().isEmpty();
        if (noAction && noDialogue) {
            issues.add(new ValidationIssue(id, "action/dialogue", "场景既无动作也无对白"));
        }
    }

    private void validateDialogue(String id, List<DialogueLine> dialogue,
                                  Set<String> known, List<ValidationIssue> issues) {
        if (dialogue == null) {
            return;
        }
        for (DialogueLine line : dialogue) {
            if (isBlank(line.line())) {
                issues.add(new ValidationIssue(id, "dialogue.line", "存在空台词"));
            }
            if (isBlank(line.character())) {
                issues.add(new ValidationIssue(id, "dialogue.character", "对白缺少说话角色"));
            } else if (!known.isEmpty() && !known.contains(line.character().strip())) {
                issues.add(new ValidationIssue(id, "dialogue.character",
                        "说话角色「" + line.character() + "」不在人物登记表内"));
            }
        }
    }

    private void validateSource(String id, Scene scene, List<ValidationIssue> issues) {
        if (scene.source() == null) {
            issues.add(new ValidationIssue(id, "source", "缺少来源信息"));
            return;
        }
        if (scene.source().chapter() <= 0) {
            issues.add(new ValidationIssue(id, "source.chapter", "来源章节号无效"));
        }
        if (isBlank(scene.source().excerpt())) {
            issues.add(new ValidationIssue(id, "source.excerpt", "缺少原文出处摘录"));
        }
    }

    /** 登记表中所有可识别的称呼（主名 + 别名）。 */
    private Set<String> knownCharacterNames(List<Character> characters) {
        Set<String> names = new LinkedHashSet<>();
        if (characters == null) {
            return names;
        }
        for (Character c : characters) {
            if (c.name() != null) {
                names.add(c.name().strip());
            }
            if (c.aliases() != null) {
                for (String a : c.aliases()) {
                    if (a != null && !a.isBlank()) {
                        names.add(a.strip());
                    }
                }
            }
        }
        return names;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
