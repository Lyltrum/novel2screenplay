package com.novel2screenplay.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * 对白类型：区分普通同期声对白与旁白/画外音/内心独白——真实剧本的常见要素。
 * 普通对白不必填本字段（留空即视为普通），仅在旁白/画外/内心时标注，保持 YAML 简洁。
 * 导出 Fountain 时映射为角色扩展标记 (V.O.)/(O.S.)。
 */
public enum DialogueType {
    NORMAL("普通"),
    NARRATION("旁白"),
    OFF_SCREEN("画外"),
    INNER("内心");

    private final String label;

    DialogueType(String label) {
        this.label = label;
    }

    @JsonValue
    public String label() {
        return label;
    }

    @JsonCreator
    public static DialogueType from(String value) {
        if (value == null) {
            return null;
        }
        String v = value.strip();
        for (DialogueType t : values()) {
            if (t.label.equals(v) || t.name().equalsIgnoreCase(v)) {
                return t;
            }
        }
        return null;
    }
}
