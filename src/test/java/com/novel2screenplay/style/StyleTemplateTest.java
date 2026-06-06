package com.novel2screenplay.style;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证风格指引映射——纯逻辑。 */
class StyleTemplateTest {

    private final StyleTemplate styleTemplate = new StyleTemplate();

    @Test
    void mapsKnownStylesToDistinctGuidance() {
        assertThat(styleTemplate.guidanceFor("电影")).contains("画面");
        assertThat(styleTemplate.guidanceFor("话剧")).contains("对白");
        assertThat(styleTemplate.guidanceFor("短剧")).contains("节奏");
        assertThat(styleTemplate.guidanceFor("分镜")).contains("镜头");
    }

    @Test
    void unknownStyleFallsBackToSeries() {
        // 默认主流形态为剧集：未知风格与 null 都回退到剧集
        assertThat(styleTemplate.guidanceFor("玄幻")).contains("剧集");
        assertThat(styleTemplate.guidanceFor(null)).contains("剧集");
    }

    @Test
    void identifiesSeriesForm() {
        // 剧集（含默认/未知）需要分集；电影/话剧/短剧/分镜为不分集的单本形态
        assertThat(styleTemplate.isSeries("剧集")).isTrue();
        assertThat(styleTemplate.isSeries(null)).isTrue();
        assertThat(styleTemplate.isSeries("电影")).isFalse();
        assertThat(styleTemplate.isSeries("分镜")).isFalse();
    }
}
