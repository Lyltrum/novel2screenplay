package com.novel2screenplay.split;

import com.novel2screenplay.model.Chapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只负责：小说全文 → 章节列表。
 * <p>
 * 策略（对真实小说鲁棒）：
 * 1. 优先用正则识别「第X章 / 第X卷 / 第X回 / 第X节 / Chapter N」这类标题行；
 * 2. 识别不到任何标题时，兜底用「分隔线（≥3 个 - = * 组成的整行）」切分；
 * 3. 仍切不出多块时，整篇作为单章返回（由上层决定是否提示「不足 3 章」）。
 */
@Component
public class ChapterSplitter {

    /** 中文/阿拉伯数字章节标题，必须独占一行（行首允许空白与全角空格）。 */
    private static final Pattern CHAPTER_HEADING = Pattern.compile(
            "(?m)^[\\s　]*(第[0-9零一二三四五六七八九十百千两]+[章卷回节][^\\n]*|(?i:chapter)\\s+\\d+[^\\n]*)$");

    /** 兜底分隔线：整行由 ≥3 个 - = * 组成。 */
    private static final Pattern SEPARATOR_LINE = Pattern.compile(
            "(?m)^[\\s　]*[-=*]{3,}[\\s　]*$");

    public List<Chapter> split(String novelText) {
        if (novelText == null || novelText.isBlank()) {
            return List.of();
        }

        List<Chapter> byHeading = splitByHeading(novelText);
        if (!byHeading.isEmpty()) {
            return byHeading;
        }

        List<Chapter> bySeparator = splitBySeparator(novelText);
        if (bySeparator.size() > 1) {
            return bySeparator;
        }

        // 兜底：整篇作为单章
        return List.of(new Chapter(1, "全文", novelText.strip()));
    }

    /** 按章节标题正则切分；标题行本身作为 title，标题之后到下一标题之前作为正文。 */
    private List<Chapter> splitByHeading(String text) {
        Matcher matcher = CHAPTER_HEADING.matcher(text);

        List<Integer> headingStarts = new ArrayList<>();
        List<Integer> bodyStarts = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        while (matcher.find()) {
            headingStarts.add(matcher.start());
            bodyStarts.add(matcher.end());
            titles.add(text.substring(matcher.start(), matcher.end()).strip());
        }

        List<Chapter> chapters = new ArrayList<>();
        for (int i = 0; i < headingStarts.size(); i++) {
            int bodyStart = bodyStarts.get(i);
            int bodyEnd = (i + 1 < headingStarts.size()) ? headingStarts.get(i + 1) : text.length();
            String body = text.substring(bodyStart, bodyEnd).strip();
            chapters.add(new Chapter(i + 1, titles.get(i), body));
        }
        return chapters;
    }

    /** 按分隔线切分；每块自动生成「第N段」标题。 */
    private List<Chapter> splitBySeparator(String text) {
        String[] parts = SEPARATOR_LINE.split(text);
        List<Chapter> chapters = new ArrayList<>();
        int index = 1;
        for (String part : parts) {
            String body = part.strip();
            if (body.isEmpty()) {
                continue;
            }
            chapters.add(new Chapter(index, "第" + index + "段", body));
            index++;
        }
        return chapters;
    }
}
