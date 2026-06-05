package com.novel2screenplay.style;

import org.springframework.stereotype.Component;

/**
 * 只负责：把改编风格映射为给模型的具体改编指引（亮点 P3，可插拔）。
 * 风格名 → 一段风格化的改编侧重说明，注入场景抽取 prompt。
 * 删除本模块不影响主干：调用方传入空指引时，模型按通用方式改编。
 */
@Component
public class StyleTemplate {

    /** 返回该风格的改编侧重指引；未知风格回退到电影。 */
    public String guidanceFor(String style) {
        String s = style == null ? "" : style.strip();
        return switch (s) {
            case "话剧" -> "话剧风格：以对白与舞台调度为核心，场景数宜少、单场时长偏长，"
                    + "对白密度高、富潜台词；动作描写精炼，聚焦人物上下场与舞台走位。";
            case "短剧" -> "短剧风格：节奏快、冲突前置，每场短促并在结尾留钩子；"
                    + "台词口语化、信息量大，避免冗长铺垫。";
            case "分镜" -> "分镜脚本风格：强调镜头感，action 用可分镜的画面化描述"
                    + "（景别、机位、运动、光影），必要时点明视觉重点。";
            default -> "电影风格：以画面叙事为主，动作与对白均衡，"
                    + "注重场景的视觉氛围与节奏起伏。";
        };
    }
}
