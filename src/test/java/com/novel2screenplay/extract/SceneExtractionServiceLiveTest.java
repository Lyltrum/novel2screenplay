package com.novel2screenplay.extract;

import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.StoryBible;
import com.novel2screenplay.split.ChapterSplitter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live 端到端：示例小说 → 切章 → 抽取第一章场景 → 序列化为 YAML。
 * 默认禁用，仅 -Dlive=true 运行：
 *   mvn test -Dlive=true -Dtest=SceneExtractionServiceLiveTest
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "live", matches = "true")
class SceneExtractionServiceLiveTest {

    @Autowired
    private ChapterSplitter splitter;
    @Autowired
    private SceneExtractionService extractor;
    @Autowired
    private YamlExporter yamlExporter;

    @Test
    void extractsFirstChapterIntoValidYaml() throws IOException {
        String novel = readSampleNovel();
        List<Chapter> chapters = splitter.split(novel);
        assertThat(chapters).hasSizeGreaterThanOrEqualTo(3);

        List<Scene> scenes = extractor.extract(chapters.get(0), StoryBible.empty(), "电影", "");
        assertThat(scenes).isNotEmpty();

        Screenplay screenplay = new Screenplay(
                "雨夜客栈（测试）", "", "电影", List.of(), scenes);
        String yaml = yamlExporter.toYaml(screenplay);
        System.out.println("===== 第一章抽取的剧本 YAML =====\n" + yaml);

        // 结构合法性
        assertThat(yaml).contains("int_ext");
        assertThat(yaml).contains("location");
        // 来源章节号已回填为 1
        assertThat(scenes.get(0).source().chapter()).isEqualTo(1);
        // 来源片段非空（亮点 P1：可追溯）
        assertThat(scenes.get(0).source().excerpt()).isNotBlank();
    }

    private String readSampleNovel() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/sample/novel.txt")) {
            assertThat(in).as("找不到 sample/novel.txt").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
