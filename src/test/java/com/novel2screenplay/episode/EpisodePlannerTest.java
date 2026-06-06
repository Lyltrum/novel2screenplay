package com.novel2screenplay.episode;

import com.novel2screenplay.model.Episode;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证分窗/重排集号的纯逻辑——长篇分集不靠模型也要保证不重不漏。 */
class EpisodePlannerTest {

    @Test
    void splitIntoWindowsPartitionsContiguouslyWithoutGapOrOverlap() {
        List<Scene> scenes = scenes(10);

        List<List<Scene>> windows = EpisodePlanner.splitIntoWindows(scenes, 4);

        // 4 + 4 + 2 = 10，窗内连续、首尾相接
        assertThat(windows).hasSize(3);
        assertThat(windows.get(0)).extracting(Scene::id).containsExactly("S1", "S2", "S3", "S4");
        assertThat(windows.get(1)).extracting(Scene::id).containsExactly("S5", "S6", "S7", "S8");
        assertThat(windows.get(2)).extracting(Scene::id).containsExactly("S9", "S10");

        // 并起来恰好等于原始全集，顺序一致 → 不重不漏
        List<String> flattened = new ArrayList<>();
        windows.forEach(w -> w.forEach(s -> flattened.add(s.id())));
        assertThat(flattened).isEqualTo(scenes.stream().map(Scene::id).toList());
    }

    @Test
    void splitIntoWindowsKeepsSingleWindowWhenUnderSize() {
        List<Scene> scenes = scenes(3);

        List<List<Scene>> windows = EpisodePlanner.splitIntoWindows(scenes, 80);

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0)).hasSize(3);
    }

    @Test
    void renumberReassignsSequentialNumbersPreservingContent() {
        // 跨窗拼接后集号会重复/乱序（5、9、2），重排应得到 1、2、3 且内容不变
        List<Episode> merged = List.of(
                new Episode(5, "甲", "梗概甲", "钩子甲", List.of("S1", "S2")),
                new Episode(9, "乙", "梗概乙", "钩子乙", List.of("S3")),
                new Episode(2, "丙", "梗概丙", "钩子丙", List.of("S4", "S5")));

        List<Episode> renumbered = EpisodePlanner.renumber(merged);

        assertThat(renumbered).extracting(Episode::number).containsExactly(1, 2, 3);
        assertThat(renumbered).extracting(Episode::title).containsExactly("甲", "乙", "丙");
        assertThat(renumbered.get(0).sceneIds()).containsExactly("S1", "S2");
        assertThat(renumbered.get(2).hook()).isEqualTo("钩子丙");
    }

    private List<Scene> scenes(int n) {
        List<Scene> list = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            list.add(new Scene("S" + i,
                    new Heading(IntExt.INT, "客栈", "夜晚"),
                    "概要", List.of(), List.of(), "", null, null));
        }
        return list;
    }
}
