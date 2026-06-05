package com.novel2screenplay.style;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证风格指引映射——纯逻辑。 */
class StyleTemplateTest {

    private final StyleTemplate styleTemplate = new StyleTemplate();

    @Test
    void mapsKnownStylesToDistinctGuidance() {
        assertThat(styleTemplate.guidanceFor("话剧")).contains("对白");
        assertThat(styleTemplate.guidanceFor("短剧")).contains("节奏");
        assertThat(styleTemplate.guidanceFor("分镜")).contains("镜头");
    }

    @Test
    void unknownStyleFallsBackToFilm() {
        assertThat(styleTemplate.guidanceFor("玄幻")).contains("电影");
        assertThat(styleTemplate.guidanceFor(null)).contains("电影");
    }
}
