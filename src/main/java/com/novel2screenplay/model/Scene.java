package com.novel2screenplay.model;

import java.util.List;

/**
 * 一个场景（剧本的基本单元，按地点/时间切分）。
 * id：全局唯一编号（S1、S2…），便于作者编辑、重排、引用。
 * summary：本场一句话概要，便于快速浏览整剧节奏。
 * action：可拍摄的动作/场景描述段落（叙述已转写为画面）。
 * transition：转场提示（如「CUT TO:」），可空。
 * source：来源可追溯（亮点 P1），指向原文出处。
 */
public record Scene(
        String id,
        Heading heading,
        String summary,
        List<String> action,
        List<DialogueLine> dialogue,
        String transition,
        SourceRef source
) {
}
