package com.novel2screenplay.export;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.novel2screenplay.model.Screenplay;
import org.springframework.stereotype.Component;

/**
 * 只负责：Screenplay → YAML 文本。
 * 自带并固化 YAML 序列化约定，集中在此一处，避免散落各处导致输出格式漂移：
 *   - SNAKE_CASE：Java 的 intExt → YAML 的 int_ext，贴合 YAML 习惯
 *   - 不输出文档头「---」
 *   - MINIMIZE_QUOTES：中文等内容尽量不加引号，更可读
 *   - 省略 null 字段（如空的 parenthetical/transition），YAML 更干净
 */
@Component
public class YamlExporter {

    private final YAMLMapper mapper;

    public YamlExporter() {
        this.mapper = YAMLMapper.builder()
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .serializationInclusion(JsonInclude.Include.NON_NULL)
                .build();
    }

    public String toYaml(Screenplay screenplay) {
        try {
            return mapper.writeValueAsString(screenplay);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("剧本序列化为 YAML 失败", e);
        }
    }
}
