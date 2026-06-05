package com.novel2screenplay.split;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 只负责：把（可能超长的）单章正文切成若干 ≤ maxChars 的文本块，用于突破单次上下文限制。
 * <p>
 * 策略：优先按段落（空行分隔）贪心打包；单段仍超限时按句末标点二次切；
 * 极端长句再按长度硬切。切点尽量落在自然边界，减少语义割裂。
 */
@Component
public class ChunkSplitter {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。！？!?…])");

    private final int maxChars;

    public ChunkSplitter(@Value("${screenplay.chunk.max-chars:1500}") int maxChars) {
        this.maxChars = maxChars;
    }

    /** 用配置的 maxChars 切块。 */
    public List<String> split(String chapterText) {
        return split(chapterText, maxChars);
    }

    /** 用指定 maxChars 切块（便于测试）。 */
    public List<String> split(String chapterText, int limit) {
        if (chapterText == null || chapterText.isBlank()) {
            return List.of();
        }
        String text = chapterText.strip();
        if (text.length() <= limit) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String para = paragraph.strip();
            if (para.isEmpty()) {
                continue;
            }
            if (para.length() > limit) {
                flush(chunks, current);
                for (String piece : hardSplit(para, limit)) {
                    chunks.add(piece);
                }
            } else if (current.length() + para.length() + 2 > limit) {
                flush(chunks, current);
                current.append(para);
            } else {
                if (current.length() > 0) {
                    current.append("\n\n");
                }
                current.append(para);
            }
        }
        flush(chunks, current);
        return chunks;
    }

    /** 超长段落：先按句末标点打包，仍超限再按长度硬切。 */
    private List<String> hardSplit(String paragraph, int limit) {
        List<String> pieces = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String sentence : SENTENCE_END.split(paragraph)) {
            if (sentence.isEmpty()) {
                continue;
            }
            if (sentence.length() > limit) {
                if (current.length() > 0) {
                    pieces.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < sentence.length(); i += limit) {
                    pieces.add(sentence.substring(i, Math.min(i + limit, sentence.length())));
                }
            } else if (current.length() + sentence.length() > limit) {
                pieces.add(current.toString());
                current.setLength(0);
                current.append(sentence);
            } else {
                current.append(sentence);
            }
        }
        if (current.length() > 0) {
            pieces.add(current.toString());
        }
        return pieces;
    }

    private void flush(List<String> chunks, StringBuilder current) {
        if (current.length() > 0) {
            chunks.add(current.toString());
            current.setLength(0);
        }
    }
}
