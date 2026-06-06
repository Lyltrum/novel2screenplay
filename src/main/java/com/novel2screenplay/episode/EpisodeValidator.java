package com.novel2screenplay.episode;

import com.novel2screenplay.model.Episode;
import com.novel2screenplay.model.Scene;
import com.novel2screenplay.model.Screenplay;
import com.novel2screenplay.validate.ValidationIssue;
import com.novel2screenplay.validate.ValidationReport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 只负责：体检剧集的分集结构，产出问题清单（不改数据）。
 * 检查项：每集有集名与集尾钩子；所有场景被"不重不漏"地分入某一集；不引用不存在的场景。
 * 分集本期不做修复闭环，问题以告警计入 X-Validation-Warnings——保证"失败可观测"。
 */
@Component
public class EpisodeValidator {

    public ValidationReport validate(Screenplay screenplay) {
        List<ValidationIssue> issues = new ArrayList<>();
        List<Episode> episodes = screenplay.episodes();

        if (episodes == null || episodes.isEmpty()) {
            issues.add(new ValidationIssue("-", "episodes", "剧集形态但未产出任何集"));
            return new ValidationReport(issues);
        }

        Set<String> allSceneIds = new LinkedHashSet<>();
        if (screenplay.scenes() != null) {
            for (Scene s : screenplay.scenes()) {
                if (s.id() != null) {
                    allSceneIds.add(s.id());
                }
            }
        }

        Set<String> covered = new HashSet<>();
        for (Episode ep : episodes) {
            String tag = "第" + ep.number() + "集";
            if (isBlank(ep.title())) {
                issues.add(new ValidationIssue(tag, "episode.title", "缺少集名"));
            }
            if (isBlank(ep.hook())) {
                issues.add(new ValidationIssue(tag, "episode.hook", "缺少集尾钩子（剧集命门）"));
            }
            if (ep.sceneIds() == null || ep.sceneIds().isEmpty()) {
                issues.add(new ValidationIssue(tag, "episode.scene_ids", "本集未包含任何场景"));
                continue;
            }
            for (String id : ep.sceneIds()) {
                if (!allSceneIds.contains(id)) {
                    issues.add(new ValidationIssue(tag, "episode.scene_ids", "引用了不存在的场景 " + id));
                } else if (!covered.add(id)) {
                    issues.add(new ValidationIssue(tag, "episode.scene_ids", "场景 " + id + " 被重复分到多集"));
                }
            }
        }

        for (String id : allSceneIds) {
            if (!covered.contains(id)) {
                issues.add(new ValidationIssue("-", "episode.scene_ids", "场景 " + id + " 未被分入任何一集"));
            }
        }

        return new ValidationReport(issues);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
