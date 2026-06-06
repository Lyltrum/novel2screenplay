package com.novel2screenplay.episode;

import com.novel2screenplay.model.Episode;
import com.novel2screenplay.model.Heading;
import com.novel2screenplay.model.IntExt;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.validate.ValidationReport;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 离线验证分集体检逻辑——纯逻辑，不需要 API key。 */
class EpisodeValidatorTest {

    private final EpisodeValidator validator = new EpisodeValidator();

    @Test
    void cleanEpisodesHaveNoIssues() {
        // S1、S2 不重不漏地分入两集，每集都有集名和钩子
        Screenplay sp = screenplay(
                List.of(scene("S1"), scene("S2")),
                List.of(
                        new Episode(1, "初遇", "梗概", "悬念A", List.of("S1")),
                        new Episode(2, "决裂", "梗概", "悬念B", List.of("S2"))));

        assertThat(validator.validate(sp).isClean()).isTrue();
    }

    @Test
    void flagsNoEpisodes() {
        Screenplay sp = screenplay(List.of(scene("S1")), List.of());

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("episodes"));
    }

    @Test
    void flagsMissingTitleAndHook() {
        Screenplay sp = screenplay(
                List.of(scene("S1")),
                List.of(new Episode(1, "  ", "梗概", null, List.of("S1"))));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues()).anyMatch(i -> i.field().equals("episode.title"));
        assertThat(report.issues()).anyMatch(i -> i.field().equals("episode.hook"));
    }

    @Test
    void flagsEpisodeWithNoScenes() {
        Screenplay sp = screenplay(
                List.of(scene("S1")),
                List.of(new Episode(1, "空集", "梗概", "钩子", List.of())));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("episode.scene_ids")
                        && i.message().contains("未包含任何场景"));
    }

    @Test
    void flagsReferenceToNonexistentScene() {
        Screenplay sp = screenplay(
                List.of(scene("S1")),
                List.of(new Episode(1, "第一集", "梗概", "钩子", List.of("S1", "S99"))));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("episode.scene_ids")
                        && i.message().contains("S99"));
    }

    @Test
    void flagsSceneAssignedToMultipleEpisodes() {
        // S1 同时被分进第 1、2 集——重复
        Screenplay sp = screenplay(
                List.of(scene("S1"), scene("S2")),
                List.of(
                        new Episode(1, "第一集", "梗概", "钩子", List.of("S1")),
                        new Episode(2, "第二集", "梗概", "钩子", List.of("S1", "S2"))));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("episode.scene_ids")
                        && i.message().contains("重复"));
    }

    @Test
    void flagsUncoveredScene() {
        // S2 没被任何一集收录——漏
        Screenplay sp = screenplay(
                List.of(scene("S1"), scene("S2")),
                List.of(new Episode(1, "第一集", "梗概", "钩子", List.of("S1"))));

        ValidationReport report = validator.validate(sp);

        assertThat(report.issues())
                .anyMatch(i -> i.field().equals("episode.scene_ids")
                        && i.message().contains("S2")
                        && i.message().contains("未被分入"));
    }

    private Screenplay screenplay(List<Scene> scenes, List<Episode> episodes) {
        return new Screenplay(null, "剧名", "梗概", "剧集", List.of(), episodes, scenes);
    }

    /** EpisodeValidator 只看 scene.id()，其余字段填最小占位即可。 */
    private Scene scene(String id) {
        return new Scene(id,
                new Heading(IntExt.INT, "客栈", "夜晚"),
                "概要", List.of(), List.of(), "", null, null);
    }
}
