package com.novel2screenplay.controller;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.Scene;

import java.util.List;

/**
 * /refine 接口的请求体（JSON）：要精修的场景 + 人物登记表（保一致）+ 修改指令。
 */
public record RefineRequest(
        Scene scene,
        List<Character> characters,
        String instruction
) {
}
