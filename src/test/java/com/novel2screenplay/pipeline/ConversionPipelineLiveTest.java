package com.novel2screenplay.pipeline;

import com.novel2screenplay.export.YamlExporter;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live 全流程：示例小说（3 章）→ 完整剧本 YAML，并写出 examples/sample-output.yml。
 * 仅 -Dlive=true 运行：
 *   mvn test -Dlive=true -Dtest=ConversionPipelineLiveTest
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "live", matches = "true")
class ConversionPipelineLiveTest {

    @Autowired
    private ConversionPipeline pipeline;
    @Autowired
    private YamlExporter yamlExporter;

    @Test
    void convertsThreeChapterNovelIntoScreenplay() throws IOException {
        String novel = readSampleNovel();

        Screenplay screenplay = pipeline.convert(novel, "电影");

        // 基本完整性
        assertThat(screenplay.title()).isNotBlank();
        assertThat(screenplay.characters()).isNotEmpty();
        assertThat(screenplay.scenes()).isNotEmpty();
        // 全局编号生效
        assertThat(screenplay.scenes()).allSatisfy(s -> assertThat(s.id()).startsWith("S"));
        assertThat(screenplay.scenes().get(0).id()).isEqualTo("S1");
        // 场景覆盖全部三章（跨章贯通）
        assertThat(screenplay.scenes())
                .extracting(s -> s.source().chapter())
                .contains(1, 2, 3);

        String yaml = yamlExporter.toYaml(screenplay);
        writeSampleOutput(yaml);
        System.out.printf("已写出 examples/sample-output.yml：人物 %d 位，场景 %d 个%n",
                screenplay.characters().size(), screenplay.scenes().size());
    }

    private String readSampleNovel() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/sample/novel.txt")) {
            assertThat(in).as("找不到 sample/novel.txt").isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void writeSampleOutput(String yaml) throws IOException {
        Path dir = Path.of("examples");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("sample-output.yml"), yaml, StandardCharsets.UTF_8);
    }
}
