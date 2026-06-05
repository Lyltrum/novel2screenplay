package com.novel2screenplay.assemble;

import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.model.SourceRef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证全局编号与组装——纯逻辑，不需要 API key。 */
class ScreenplayAssemblerTest {

    private final ScreenplayAssembler assembler = new ScreenplayAssembler();

    @Test
    void reassignsGlobalSceneIdsRegardlessOfModelOutput() {
        // 模拟模型给出的混乱 id（重复、从 1 开始）
        Scene a = sceneWithId("1");
        Scene b = sceneWithId("1");
        Scene c = sceneWithId(null);

        Screenplay sp = assembler.assemble("剧名", "梗概", "电影", List.of(), List.of(a, b, c));

        assertThat(sp.scenes()).extracting(Scene::id).containsExactly("S1", "S2", "S3");
        assertThat(sp.title()).isEqualTo("剧名");
        // 其余字段原样保留
        assertThat(sp.scenes().get(0).source().chapter()).isEqualTo(1);
    }

    @Test
    void nullCharactersBecomeEmptyList() {
        Screenplay sp = assembler.assemble("t", "l", "电影", null, List.of());
        assertThat(sp.characters()).isEmpty();
        assertThat(sp.scenes()).isEmpty();
    }

    private Scene sceneWithId(String id) {
        return new Scene(id, null, "概要", List.of(), List.of(), null, new SourceRef(1, "原文"), null);
    }
}
