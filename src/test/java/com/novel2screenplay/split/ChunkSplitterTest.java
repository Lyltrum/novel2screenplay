package com.novel2screenplay.split;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证章内分块——纯逻辑。 */
class ChunkSplitterTest {

    private final ChunkSplitter splitter = new ChunkSplitter(1500);

    @Test
    void shortTextStaysSingleChunk() {
        List<String> chunks = splitter.split("短短一段。", 1500);
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("短短一段。");
    }

    @Test
    void packsParagraphsWithinLimit() {
        String text = "第一段，十个字符。\n\n第二段，也十个字。\n\n第三段，凑数用的。";
        // 限制 12 字符：每段约 8~9 字，应切成多块，且每块不超限
        List<String> chunks = splitter.split(text, 12);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(12));
    }

    @Test
    void hardSplitsOverlongSingleParagraph() {
        // 单段无空行、无句末标点，长度 30，限制 10 → 必须硬切成 3 块
        String text = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十";
        List<String> chunks = splitter.split(text, 10);

        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(10));
        // 内容无损
        assertThat(String.join("", chunks)).isEqualTo(text);
    }

    @Test
    void splitsLongParagraphBySentenceBoundary() {
        String text = "他拔剑出鞘。寒光乍现。对手应声倒地。雨还在下。";
        List<String> chunks = splitter.split(text, 12);

        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(12));
        // 句子不被从中间切断（每块都以句末标点结尾）
        assertThat(chunks).allSatisfy(c -> assertThat(c).matches(".*[。！？]$"));
    }

    @Test
    void emptyInputReturnsEmpty() {
        assertThat(splitter.split("  ", 100)).isEmpty();
        assertThat(splitter.split(null, 100)).isEmpty();
    }
}
