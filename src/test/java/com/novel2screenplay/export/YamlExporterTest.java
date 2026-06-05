package com.novel2screenplay.export;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.DialogueLine;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SceneCraft;
import com.novel2screenplay.model.SceneFunction;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.ScreenplayMeta;
import com.novel2screenplay.model.SourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 离线验证「YAML 输出契约」——不依赖 Spring 容器、不需要 API key、不烧 token。
 * 目的：把剧本的 YAML 输出格式钉死，作为后续所有抽取/导出的目标形态。
 */
class YamlExporterTest {

    @Test
    void serializesScreenplayToReadableSnakeCaseYaml() {
        Screenplay screenplay = sampleScreenplay();

        String yaml = new YamlExporter().toYaml(screenplay);
        System.out.println("===== 生成的 YAML =====\n" + yaml);

        // 字段名转蛇形：intExt -> int_ext，timeOfDay -> time_of_day
        assertThat(yaml).contains("int_ext:");
        assertThat(yaml).contains("time_of_day:");
        // int_ext 序列化为中文
        assertThat(yaml).contains("内");
        // 顶层溯源块
        assertThat(yaml).contains("meta:");
        assertThat(yaml).contains("source_chapters:");
        // 不带文档头 "---"
        assertThat(yaml).doesNotStartWith("---");
        // 中文内容保留
        assertThat(yaml).contains("客栈大堂");
        assertThat(yaml).contains("天生我材必有用");
        // 来源可追溯字段在（亮点 P1）
        assertThat(yaml).contains("source:");
        assertThat(yaml).contains("chapter:");
        assertThat(yaml).contains("excerpt:");
        // 编剧笔记注释层在
        assertThat(yaml).contains("craft:");
        assertThat(yaml).contains("objective:");
        assertThat(yaml).contains("turn:");
        assertThat(yaml).contains("REVEAL_CHARACTER");
        // 人物登记表的别名在
        assertThat(yaml).contains("aliases:");
    }

    private Screenplay sampleScreenplay() {
        Heading heading = new Heading(IntExt.INT, "客栈大堂", "夜");
        DialogueLine dialogue = new DialogueLine("李白", "(举杯)", "天生我材必有用。", null);
        SourceRef source = new SourceRef(1, "李白独坐客栈一角，举杯长叹……");
        Scene scene = new Scene(
                "S1",
                heading,
                "李白夜宿客栈，借酒抒怀。",
                List.of("油灯昏黄，李白独坐角落，剑横膝上。"),
                List.of(dialogue),
                "CUT TO:",
                source,
                new SceneCraft("借酒排遣胸中郁结", "抱负与世道的落差", "举杯却未饮，郁结更深",
                        SceneFunction.REVEAL_CHARACTER));
        Character liBai = new Character("李白", List.of("太白", "谪仙人"), "唐代诗人，豪放不羁，仗剑远游。");
        return new Screenplay(
                new ScreenplayMeta(3, 1),
                "侠客行",
                "一个诗人仗剑天涯、以酒会友的故事。",
                "电影",
                List.of(liBai),
                List.of(scene));
    }
}
