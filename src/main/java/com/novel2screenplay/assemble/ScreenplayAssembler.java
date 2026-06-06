package com.novel2screenplay.assemble;

import com.novel2screenplay.model.Character;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.ScreenplayMeta;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 只负责：把各章抽取出的场景组装成顶层 Screenplay，并对场景做全局统一编号（S1、S2…）。
 * 统一在此处编号，可消除模型自行编号的不一致（如重复、从 1 开始、跨章冲突）。
 */
@Component
public class ScreenplayAssembler {

    public Screenplay assemble(int sourceChapters,
                               String title,
                               String logline,
                               String style,
                               List<Character> characters,
                               List<Scene> scenes) {
        List<Scene> numbered = new ArrayList<>(scenes.size());
        int seq = 1;
        for (Scene scene : scenes) {
            numbered.add(withId(scene, "S" + seq));
            seq++;
        }
        ScreenplayMeta meta = new ScreenplayMeta(sourceChapters, numbered.size());
        return new Screenplay(
                meta,
                title,
                logline,
                style,
                characters == null ? List.of() : characters,
                null,
                numbered);
    }

    private Scene withId(Scene scene, String id) {
        return new Scene(
                id,
                scene.heading(),
                scene.summary(),
                scene.action(),
                scene.dialogue(),
                scene.transition(),
                scene.source(),
                scene.craft());
    }
}
