package com.novel2screenplay.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 内景 / 外景 / 内外景，对标剧本 slugline 的内外景标记。
 * 用枚举受控取值；在 YAML 中序列化为中文「内/外/内外」，与全中文内容保持一致；
 * 导出 Fountain 时再映射回行业标准 INT./EXT.（见 FountainExporter）。
 */
public enum IntExt {
    INT("内"),
    EXT("外"),
    INT_EXT("内外");

    private final String label;

    IntExt(String label) {
        this.label = label;
    }

    /** YAML/JSON 中以中文呈现。 */
    @JsonValue
    public String label() {
        return label;
    }

    /** 兼容中文「内/外/内外」与英文 INT/EXT/INT_EXT 两种输入，鲁棒反序列化。 */
    @JsonCreator
    public static IntExt from(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        for (IntExt e : values()) {
            if (e.label.equals(v) || e.name().equalsIgnoreCase(v)) {
                return e;
            }
        }
        return null;
    }
}
