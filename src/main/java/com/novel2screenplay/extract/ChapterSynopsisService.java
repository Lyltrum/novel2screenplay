package com.novel2screenplay.extract;

import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.prompt.Prompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 只负责：为单章生成一句话剧情梗概，作为后续场景抽取的"前情提要"素材。
 * <p>
 * 该调用只依赖本章自身正文 ⟹ 各章可并行预算；把每章梗概预先算好再拼接成前情，
 * 场景抽取就不必"等前面章节抽完"，从而摆脱串行依赖、整体可并行——
 * 这是用一次极短调用换来抽取阶段并行化、且不丢跨章连贯的关键。
 */
@Service
public class ChapterSynopsisService {

    private final ChatClient chatClient;

    public ChapterSynopsisService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /** 生成本章一句话梗概（纯文本，控制在很短篇幅）。 */
    public String summarize(Chapter chapter) {
        return chatClient.prompt()
                .user(Prompts.chapterSynopsis(chapter))
                .call()
                .content();
    }
}
