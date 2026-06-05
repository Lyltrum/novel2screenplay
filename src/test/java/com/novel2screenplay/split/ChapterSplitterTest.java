package com.novel2screenplay.split;

import com.novel2screenplay.model.Chapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证章节切分——纯正则，不需要 API key、不烧 token。 */
class ChapterSplitterTest {

    private final ChapterSplitter splitter = new ChapterSplitter();

    @Test
    void splitsByChineseNumberHeadings() {
        String novel = """
                第一章 初遇
                李白在客栈遇见杜甫。
                第二章 同行
                二人结伴而行。
                第三章 别离
                长安城外，挥手作别。
                """;

        List<Chapter> chapters = splitter.split(novel);

        assertThat(chapters).hasSize(3);
        assertThat(chapters.get(0).index()).isEqualTo(1);
        assertThat(chapters.get(0).title()).isEqualTo("第一章 初遇");
        assertThat(chapters.get(0).text()).contains("客栈遇见杜甫");
        assertThat(chapters.get(2).title()).isEqualTo("第三章 别离");
        assertThat(chapters.get(2).text()).contains("挥手作别");
    }

    @Test
    void splitsByArabicAndChapterKeyword() {
        String novel = """
                第1章
                正文一。
                Chapter 2
                正文二。
                """;

        List<Chapter> chapters = splitter.split(novel);

        assertThat(chapters).hasSize(2);
        assertThat(chapters.get(0).title()).isEqualTo("第1章");
        assertThat(chapters.get(1).title()).isEqualTo("Chapter 2");
    }

    @Test
    void fallsBackToSeparatorLinesWhenNoHeadings() {
        String novel = """
                第一段正文，没有章节标题。
                ===
                第二段正文。
                ===
                第三段正文。
                """;

        List<Chapter> chapters = splitter.split(novel);

        assertThat(chapters).hasSize(3);
        assertThat(chapters.get(0).title()).isEqualTo("第1段");
        assertThat(chapters.get(0).text()).contains("没有章节标题");
        assertThat(chapters.get(2).text()).contains("第三段正文");
    }

    @Test
    void singleChapterWhenNoHeadingNorSeparator() {
        String novel = "就是一段没有任何分隔的连续文本。";

        List<Chapter> chapters = splitter.split(novel);

        assertThat(chapters).hasSize(1);
        assertThat(chapters.get(0).index()).isEqualTo(1);
        assertThat(chapters.get(0).title()).isEqualTo("全文");
    }

    @Test
    void returnsEmptyForBlankInput() {
        assertThat(splitter.split("   ")).isEmpty();
        assertThat(splitter.split(null)).isEmpty();
    }
}
