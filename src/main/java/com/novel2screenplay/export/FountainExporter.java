package com.novel2screenplay.export;

import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.SourceRef;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 只负责：Screenplay → Fountain 行业标准剧本文本（亮点 P2）。
 * Fountain 是被 Final Draft 等专业软件广泛支持的纯文本剧本格式，便于作者导入后续工具。
 * 约定：
 *   - 场景标题：INT./EXT. 地点 - 时间  #场景号#
 *   - 强制角色行用 @ 前缀（兼容中文名）；括号提示独占一行；转场用 > 前缀
 *   - 来源出处用 Fountain 笔记 [[ ]] 保留（渲染时通常隐藏，亮点 P1 在 Fountain 中也可追溯）
 */
@Component
public class FountainExporter {

    public String toFountain(Screenplay screenplay) {
        StringBuilder sb = new StringBuilder();

        // 标题页
        sb.append("Title: ").append(nullToEmpty(screenplay.title())).append('\n');
        if (notBlank(screenplay.logline())) {
            sb.append("Logline: ").append(screenplay.logline().strip()).append('\n');
        }
        if (notBlank(screenplay.style())) {
            sb.append("Style: ").append(screenplay.style().strip()).append('\n');
        }
        sb.append('\n');

        for (Scene scene : screenplay.scenes()) {
            appendScene(sb, scene);
        }
        return sb.toString();
    }

    private void appendScene(StringBuilder sb, Scene scene) {
        sb.append(sluglineOf(scene)).append("\n\n");

        appendSourceNote(sb, scene.source());
        appendCraftNote(sb, scene.craft());

        if (scene.action() != null) {
            for (String action : scene.action()) {
                if (notBlank(action)) {
                    sb.append(action.strip()).append("\n\n");
                }
            }
        }

        if (scene.dialogue() != null) {
            for (DialogueLine line : scene.dialogue()) {
                appendDialogue(sb, line);
            }
        }

        if (notBlank(scene.transition())) {
            sb.append("> ").append(scene.transition().strip()).append("\n\n");
        }
    }

    private String sluglineOf(Scene scene) {
        Heading heading = scene.heading();
        String prefix = intExtPrefix(heading == null ? null : heading.intExt());
        String location = heading == null ? "" : nullToEmpty(heading.location());
        String time = (heading == null || !notBlank(heading.timeOfDay())) ? "" : " - " + heading.timeOfDay().strip();
        String number = notBlank(scene.id()) ? "  #" + scene.id().strip() + "#" : "";
        return prefix + " " + location + time + number;
    }

    /** 对白类型映射为 Fountain 角色扩展标记：旁白/内心→(V.O.)，画外→(O.S.)，普通→无。 */
    private String extensionOf(com.novel2screenplay.model.DialogueType type) {
        if (type == null) {
            return "";
        }
        return switch (type) {
            case NARRATION, INNER -> " (V.O.)";
            case OFF_SCREEN -> " (O.S.)";
            case NORMAL -> "";
        };
    }

    private String intExtPrefix(IntExt intExt) {
        if (intExt == null) {
            return "INT.";
        }
        return switch (intExt) {
            case INT -> "INT.";
            case EXT -> "EXT.";
            case INT_EXT -> "INT./EXT.";
        };
    }

    private void appendSourceNote(StringBuilder sb, SourceRef source) {
        if (source != null && notBlank(source.excerpt())) {
            sb.append("[[来源 第").append(source.chapter()).append("章：")
                    .append(source.excerpt().strip()).append("]]\n\n");
        }
    }

    /** 编剧笔记以 Fountain 笔记 [[ ]] 形式保留（渲染时通常隐藏，但随剧本传递）。 */
    private void appendCraftNote(StringBuilder sb, SceneCraft craft) {
        if (craft == null) {
            return;
        }
        StringBuilder note = new StringBuilder("[[编剧笔记");
        if (notBlank(craft.objective())) {
            note.append(" 目标:").append(craft.objective().strip());
        }
        if (notBlank(craft.conflict())) {
            note.append(" 冲突:").append(craft.conflict().strip());
        }
        if (notBlank(craft.turn())) {
            note.append(" 转折:").append(craft.turn().strip());
        }
        if (craft.function() != null) {
            note.append(" 职能:").append(craft.function());
        }
        note.append("]]");
        sb.append(note).append("\n\n");
    }

    private void appendDialogue(StringBuilder sb, DialogueLine line) {
        if (line == null || !notBlank(line.character()) || !notBlank(line.line())) {
            return;
        }
        sb.append('@').append(line.character().strip()).append(extensionOf(line.type())).append('\n');
        if (notBlank(line.parenthetical())) {
            String p = line.parenthetical().strip();
            if (!p.startsWith("(") && !p.startsWith("（")) {
                p = "(" + p + ")";
            }
            sb.append(p).append('\n');
        }
        sb.append(line.line().strip()).append("\n\n");
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s.strip();
    }
}
