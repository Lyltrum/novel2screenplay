package com.novel2screenplay.validate;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.Screenplay;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
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

    /** action 里的心理描写/抽象判断标记（"展示而非讲述"的反例），命中即需外化。 */
    private static final Pattern INTERIORITY = Pattern.compile(
            "心想|心里|心中|内心|心头|暗想|暗自|暗暗|思忖|寻思|意识到|回忆起|想起|不由得想");

    /** 单句对白超过此字数视为疑似信息倾倒。 */
    private static final int EXPOSITION_MAX_CHARS = 80;

    /** 体检整部剧本（含顶层 title/logline 与每个场景）。 */
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
            addSceneIssues(scene, known, issues);
        }
        return new ValidationReport(issues);
    }

    /** 只体检单个场景（用于交互式精修，不涉及顶层字段）。 */
    public ValidationReport validateScene(Scene scene, List<Character> characters) {
        List<ValidationIssue> issues = new ArrayList<>();
        addSceneIssues(scene, knownCharacterNames(characters), issues);
        return new ValidationReport(issues);
    }

    private void addSceneIssues(Scene scene, Set<String> known, List<ValidationIssue> issues) {
        String id = scene.id();
        validateHeading(id, scene.heading(), issues);
        validateContent(id, scene, issues);
        validateShowDontTell(id, scene.action(), issues);
        validateDialogue(id, scene.dialogue(), known, issues);
        validateSource(id, scene, issues);
        validateCraft(id, scene.craft(), issues);
    }

    /** 展示而非讲述：action 不应含心理描写/抽象判断，须外化为可拍摄画面。 */
    private void validateShowDontTell(String id, List<String> action, List<ValidationIssue> issues) {
        if (action == null) {
            return;
        }
        for (String line : action) {
            if (line == null) {
                continue;
            }
            Matcher m = INTERIORITY.matcher(line);
            if (m.find()) {
                issues.add(new ValidationIssue(id, "action",
                        "动作含心理描写「" + m.group() + "」，应外化为可拍摄的画面/动作/表情"));
            }
        }
    }

    /** 编剧笔记完整性：每场须有目标/冲突/转折/职能。 */
    private void validateCraft(String id, SceneCraft craft, List<ValidationIssue> issues) {
        if (craft == null) {
            issues.add(new ValidationIssue(id, "craft", "缺少编剧笔记(目标/冲突/转折/职能)"));
            return;
        }
        if (isBlank(craft.objective())) {
            issues.add(new ValidationIssue(id, "craft.objective", "缺少本场目标(角色想要什么)"));
        }
        if (isBlank(craft.conflict())) {
            issues.add(new ValidationIssue(id, "craft.conflict", "缺少本场冲突(阻碍是什么)"));
        }
        if (isBlank(craft.turn())) {
            issues.add(new ValidationIssue(id, "craft.turn", "缺少本场转折(无变化的死场景应合并)"));
        }
        if (craft.function() == null) {
            issues.add(new ValidationIssue(id, "craft.function", "缺少戏剧职能"));
        }
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
            } else if (line.line().strip().length() > EXPOSITION_MAX_CHARS) {
                issues.add(new ValidationIssue(id, "dialogue.line",
                        "对白过长(" + line.line().strip().length() + "字)，疑似信息倾倒，建议拆短或外化"));
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
