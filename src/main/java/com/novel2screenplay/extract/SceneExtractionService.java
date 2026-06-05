package com.novel2screenplay.extract;

import com.novel2screenplay.model.Chapter;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.SourceRef;
import com.novel2screenplay.model.StoryBible;
import com.novel2screenplay.model.extraction.SceneExtraction;
import com.novel2screenplay.prompt.Prompts;
import com.novel2screenplay.style.StyleTemplate;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 只负责：单章正文 → 场景列表。
 * 注入 StoryBible（人名归一）与改编风格指引，调用 Qwen 结构化输出抽取场景。
 * source.chapter 由程序回填（比让模型猜更可靠），id 留待 assembler 全局编号。
 */
@Service
public class SceneExtractionService {

    private final ChatClient chatClient;
    private final StyleTemplate styleTemplate;

    public SceneExtractionService(ChatClient chatClient, StyleTemplate styleTemplate) {
        this.chatClient = chatClient;
        this.styleTemplate = styleTemplate;
    }

    public List<Scene> extract(Chapter chapter, StoryBible bible, String style) {
        String styleGuidance = styleTemplate.guidanceFor(style);
        SceneExtraction result = chatClient.prompt()
                .user(Prompts.sceneExtraction(chapter, bible, style, styleGuidance))
                .call()
                .entity(SceneExtraction.class);

        if (result == null || result.scenes() == null) {
            return List.of();
        }
        return result.scenes().stream()
                .map(scene -> fillSourceChapter(scene, chapter.index()))
                .toList();
    }

    /** 回填来源章节号，保留模型摘录的原文片段。 */
    private Scene fillSourceChapter(Scene scene, int chapterIndex) {
        String excerpt = (scene.source() != null) ? scene.source().excerpt() : null;
        SourceRef source = new SourceRef(chapterIndex, excerpt);
        return new Scene(
                scene.id(),
                scene.heading(),
                scene.summary(),
                scene.action(),
                scene.dialogue(),
                scene.transition(),
                source);
    }
}
