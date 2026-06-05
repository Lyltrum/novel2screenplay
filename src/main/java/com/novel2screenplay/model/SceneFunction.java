package com.novel2screenplay.model;

/**
 * 场景的主要戏剧职能（编剧笔记）。用枚举受控取值，便于校验与统计全剧结构。
 * 一场好戏至少要承担其中一种职能；什么都不承担的场景应被合并或删除。
 */
public enum SceneFunction {
    /** 推进剧情：把故事往前推。 */
    ADVANCE_PLOT,
    /** 塑造人物：揭示性格、关系或动机。 */
    REVEAL_CHARACTER,
    /** 升级冲突：把对抗或张力推高一层。 */
    ESCALATE_CONFLICT,
    /** 铺垫：埋设后续会回收的信息或伏笔。 */
    SETUP,
    /** 回收：兑现此前的铺垫或伏笔。 */
    PAYOFF
}
