package com.novel2screenplay.validate;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.SourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证体检逻辑——纯逻辑，不需要 API key。 */
class SceneValidatorTest {

    private final SceneValidator validator = new SceneValidator();

    @Test
    void cleanScreenplayHasNoIssues() {
        Screenplay sp = new Screenplay("剧名", "梗概", "电影",
                List.of(new Character("沈砚", List.of("沈三郎"), "剑客")),
                List.of(validScene("S1", "沈砚")));

        assertThat(validator.validate(sp).isClean()).isTrue();
    }

    @Test
    void flagsEnglishTimeOfDay() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.EXT, "后巷", "Night"),
                "概要", List.of("黑影翻墙"), List.of(), "", new SourceRef(2, "原文"));
        Screenplay sp = new Screenplay("t", "l", "电影", List.of(), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("heading.time_of_day"));
    }

    @Test
    void flagsDialogueCharacterNotInRegistry() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "大堂", "夜晚"),
                "概要", List.of(),
                List.of(new DialogueLine("路人甲", "", "你好")),
                "", new SourceRef(1, "原文"));
        Screenplay sp = new Screenplay("t", "l", "电影",
                List.of(new Character("沈砚", List.of(), "剑客")),
                List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("dialogue.character")
                        && i.message().contains("路人甲"));
    }

    @Test
    void flagsMissingSourceAndEmptyContent() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "大堂", "夜晚"),
                "概要", List.of(), List.of(), "", null);
        Screenplay sp = new Screenplay("t", "l", "电影", List.of(), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("source"));
        assertThat(report.issues()).anyMatch(i -> i.field().equals("action/dialogue"));
    }

    private Scene validScene(String id, String speaker) {
        return new Scene(id,
                new Heading(IntExt.INT, "听雨楼客栈大堂", "夜晚"),
                "概要",
                List.of("油灯昏黄"),
                List.of(new DialogueLine(speaker, "(淡淡)", "请便。")),
                "",
                new SourceRef(1, "暮色四合，沈砚独坐角落。"));
    }
}
