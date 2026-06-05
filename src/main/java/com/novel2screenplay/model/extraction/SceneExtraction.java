package com.novel2screenplay.model.extraction;

import com.novel2screenplay.model.Scene;

import java.util.List;

/**
 * 场景抽取调用的包装 record（仅内部使用，不进最终 YAML）。
 * 用对象包一层 scenes，比让模型直接吐顶层数组更稳定可靠。
 */
public record SceneExtraction(
        List<Scene> scenes
) {
}
