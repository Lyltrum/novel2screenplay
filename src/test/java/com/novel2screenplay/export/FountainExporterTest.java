package com.novel2screenplay.export;

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

/** 离线验证 Fountain 导出格式——纯逻辑，不需要 API key。 */
class FountainExporterTest {

    private final FountainExporter exporter = new FountainExporter();

    @Test
    void exportsScreenplayToFountain() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.INT, "听雨楼客栈大堂", "黄昏"),
                "概要",
                List.of("油灯昏黄，沈砚独坐角落。"),
                List.of(new DialogueLine("沈砚", "淡淡", "请便。")),
                "切至",
                new SourceRef(1, "暮色四合，沈砚独坐角落。"));
        Screenplay sp = new Screenplay("铁面青衫", "剑客追查旧案。", "电影",
                List.of(new Character("沈砚", List.of("沈三郎"), "剑客")),
                List.of(scene));

        String fountain = exporter.toFountain(sp);
        System.out.println(fountain);

        // 标题页
        assertThat(fountain).contains("Title: 铁面青衫");
        // slugline：INT. 地点 - 时间  #场景号#
        assertThat(fountain).contains("INT. 听雨楼客栈大堂 - 黄昏  #S1#");
        // 强制角色行 + 括号提示自动补全 + 台词
        assertThat(fountain).contains("@沈砚");
        assertThat(fountain).contains("(淡淡)");
        assertThat(fountain).contains("请便。");
        // 转场强制前缀
        assertThat(fountain).contains("> 切至");
        // 来源笔记（亮点 P1 在 Fountain 中可追溯）
        assertThat(fountain).contains("[[来源 第1章：");
    }

    @Test
    void skipsEmptyDialogueLines() {
        Scene scene = new Scene("S1",
                new Heading(IntExt.EXT, "后巷", "午夜"),
                "概要", List.of("黑影翻墙。"),
                List.of(new DialogueLine("沈砚", "", "")),
                "", new SourceRef(2, "原文"));
        Screenplay sp = new Screenplay("t", "l", "电影", List.of(), List.of(scene));

        String fountain = exporter.toFountain(sp);

        // 空台词不应产生孤立的 @角色 行
        assertThat(fountain).doesNotContain("@沈砚");
        assertThat(fountain).contains("EXT. 后巷 - 午夜");
    }
}
