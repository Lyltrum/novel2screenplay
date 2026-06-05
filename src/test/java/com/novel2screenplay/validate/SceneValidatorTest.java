package com.novel2screenplay.validate;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.SceneFunction;
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
        Screenplay sp = new Screenplay(null, "剧名", "梗概", "电影",
                List.of(new Character("沈砚", List.of("沈三郎"), "剑客")),
                List.of(validScene("S1", "沈砚")));

        assertThat(validator.validate(sp).isClean()).isTrue();
    }

    @Test
    void flagsEnglishTimeOfDay() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.EXT, "后巷", "Night"),
                "概要", List.of("黑影翻墙"), List.of(), "", new SourceRef(2, "原文"), null);
        Screenplay sp = new Screenplay(null, "t", "l", "电影", List.of(), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("heading.time_of_day"));
    }

    @Test
    void flagsDialogueCharacterNotInRegistry() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "大堂", "夜晚"),
                "概要", List.of(),
                List.of(new DialogueLine("路人甲", "", "你好", null)),
                "", new SourceRef(1, "原文"), null);
        Screenplay sp = new Screenplay(null, "t", "l", "电影",
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
                "概要", List.of(), List.of(), "", null, null);
        Screenplay sp = new Screenplay(null, "t", "l", "电影", List.of(), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("source"));
        assertThat(report.issues()).anyMatch(i -> i.field().equals("action/dialogue"));
    }

    @Test
    void flagsInteriorityInAction() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "客栈", "夜晚"),
                "概要",
                List.of("沈砚心里一紧，暗暗握紧了剑。"),
                List.of(),
                "", new SourceRef(1, "原文"),
                new SceneCraft("查探", "对方戒备", "起疑", SceneFunction.SETUP));
        Screenplay sp = new Screenplay(null, "t", "l", "电影", List.of(), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("action")
                && i.message().contains("心理描写"));
    }

    @Test
    void flagsExpositionDumpDialogue() {
        String longLine = "你可知道二十年前黑风寨血洗临川镇的那桩旧案，原本正是你父亲当年与寨主暗中勾结、里应外合所一手酿成的，"
                + "而我之所以隐姓埋名、忍辱负重潜伏至今，就是为了查清当年的全部真相，替我那一夜之间惨死的满门全家报仇雪恨。";
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "客栈", "夜晚"),
                "概要", List.of("两人对坐。"),
                List.of(new DialogueLine("沈砚", "", longLine, null)),
                "", new SourceRef(1, "原文"),
                new SceneCraft("摊牌", "对方否认", "真相揭开", SceneFunction.PAYOFF));
        Screenplay sp = new Screenplay(null, "t", "l", "电影",
                List.of(new Character("沈砚", List.of(), "剑客")), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("dialogue.line")
                && i.message().contains("信息倾倒"));
    }

    @Test
    void flagsMissingCraft() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "客栈", "夜晚"),
                "概要", List.of("油灯昏黄。"), List.of(), "",
                new SourceRef(1, "原文"), null);
        Screenplay sp = new Screenplay(null, "t", "l", "电影", List.of(), List.of(scene));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("craft"));
    }

    private Scene validScene(String id, String speaker) {
        return new Scene(id,
                new Heading(IntExt.INT, "听雨楼客栈大堂", "夜晚"),
                "概要",
                List.of("油灯昏黄"),
                List.of(new DialogueLine(speaker, "(淡淡)", "请便。", null)),
                "",
                new SourceRef(1, "暮色四合，沈砚独坐角落。"),
                new SceneCraft("观察来客", "戒备与试探", "允许同座，关系破冰", SceneFunction.REVEAL_CHARACTER));
    }
}
