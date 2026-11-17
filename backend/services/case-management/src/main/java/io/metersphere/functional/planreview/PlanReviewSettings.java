package io.metersphere.functional.planreview;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Validated settings; option sets must come from current project-authorized server queries. */
public record PlanReviewSettings(String name, String moduleId, List<String> reviewers, String reviewPassRule,
                                 boolean followPlanName) {
    public PlanReviewSettings(String name, String moduleId, List<String> reviewers, String reviewPassRule) {
        this(name, moduleId, reviewers, reviewPassRule, false);
    }
    public PlanReviewSettings {
        if (name == null || name.strip().isEmpty() || name.strip().length() > 255) {
            throw new IllegalArgumentException("评审名称不能为空且不能超过255字符");
        }
        name = name.strip();
        if (moduleId == null || moduleId.isBlank() || moduleId.length() > 50) {
            throw new IllegalArgumentException("请选择合法评审目录");
        }
        if (reviewers == null || reviewers.isEmpty()
                || reviewers.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 50)
                || reviewers.stream().distinct().count() != reviewers.size()) {
            throw new IllegalArgumentException("请至少选择一名有效评审人，不能重复");
        }
        reviewers = List.copyOf(reviewers);
        if (!Set.of("SINGLE", "MULTIPLE").contains(Objects.toString(reviewPassRule, ""))) {
            throw new IllegalArgumentException("请选择合法评审通过规则");
        }
    }

    public static String defaultName(String planName) {
        if (planName == null || planName.strip().isEmpty()) throw new IllegalArgumentException("计划名称不能为空");
        String base = planName.strip();
        int end = Math.min(252, base.length());
        if (end < base.length() && end > 0 && Character.isHighSurrogate(base.charAt(end - 1))) end--;
        return base.substring(0, end) + "-评审";
    }

    public PlanReviewSettings forPlanName(String planName) {
        return followPlanName ? new PlanReviewSettings(defaultName(planName), moduleId, reviewers, reviewPassRule, true) : this;
    }

    public void validateOptions(Set<String> legalModules, Set<String> eligibleReviewers, Set<String> legalRules) {
        if (!legalModules.contains(moduleId) || !eligibleReviewers.containsAll(reviewers)
                || !legalRules.contains(reviewPassRule)) {
            throw new IllegalArgumentException("目录、评审人或规则已失效，请重新选择");
        }
    }
}
