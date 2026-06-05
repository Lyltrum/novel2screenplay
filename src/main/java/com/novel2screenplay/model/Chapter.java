package com.novel2screenplay.model;

/**
 * 一个章节（仅内部使用，不进最终 YAML）。
 * index：章节序号（从 1 计），同时作为 SourceRef.chapter 的来源依据。
 * title：章节标题行（如「第一章 初遇」），无标题时由切分器兜底生成。
 * text：本章正文。
 */
public record Chapter(
        int index,
        String title,
        String text
) {
}
