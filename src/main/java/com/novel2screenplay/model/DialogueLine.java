package com.novel2screenplay.model;

/**
 * 一句对白：说话角色 + 括号提示（parenthetical）+ 台词。
 * character 必须出现在 Screenplay.characters 登记表内（由校验环节保证跨场景一致）。
 * parenthetical 可空，如「(冷笑)」「(压低声音)」。
 */
public record DialogueLine(
        String character,
        String parenthetical,
        String line
) {
}
