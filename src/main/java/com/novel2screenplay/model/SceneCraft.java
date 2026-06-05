package com.novel2screenplay.model;

/**
 * 场景的"编剧笔记"——与可拍摄的剧本正文分离的戏剧注释层。
 * 它不是被拍摄的内容，而是说明"这场戏为什么存在、在做什么"，逼迫改编时进行戏剧思考，
 * 也供作者打磨时判断每场是否站得住。
 *
 * objective：本场主驱动角色想要什么（戏剧目标）。
 * conflict：阻碍该目标的对抗力量是什么。
 * turn：本场结束时发生了什么变化（情势/关系/情绪/信息的翻转）——没有变化的场景是死的。
 * function：本场承担的主要戏剧职能。
 */
public record SceneCraft(
        String objective,
        String conflict,
        String turn,
        SceneFunction function
) {
}
