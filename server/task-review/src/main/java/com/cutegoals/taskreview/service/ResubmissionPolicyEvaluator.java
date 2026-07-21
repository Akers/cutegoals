package com.cutegoals.taskreview.service;

import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskTemplate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 重复提交策略评估器。
 * 被 TaskReviewService.submitAttempt 和 TaskAssignmentService.listByChild 共享调用。
 *
 * D9 回退：V14 之前老 assignment 的 snapshot 字段为 NULL 时，由 caller 传入 template 当前值。
 */
public class ResubmissionPolicyEvaluator {

    public ResubmissionDecision evaluate(TaskAssignment assignment,
                                          long approvedCount, long earnedPoints) {
        return evaluate(assignment, approvedCount, earnedPoints, null);
    }

    public ResubmissionDecision evaluate(TaskAssignment assignment,
                                          long approvedCount, long earnedPoints,
                                          TaskTemplate template) {
        Boolean allowResubmit = assignment.getSnapshotTemplateAllowResubmit();
        Integer maxSubmissions = assignment.getSnapshotTemplateMaxSubmissions();
        Integer pointsCap = assignment.getSnapshotTemplatePointsCap();

        // D9 回退：V14 之前老 assignment snapshot 为 NULL，读 template 当前值
        if (allowResubmit == null && template != null) {
            allowResubmit = template.getAllowResubmit();
            maxSubmissions = template.getMaxSubmissions();
            pointsCap = template.getPointsCap();
        }

        // 仍为 NULL 或明确禁用 → 不校验
        if (allowResubmit == null || Boolean.FALSE.equals(allowResubmit)) {
            return ResubmissionDecision.allowed();
        }

        // max 校验：max=0 不限制
        if (maxSubmissions != null && maxSubmissions > 0
                && approvedCount >= maxSubmissions) {
            return ResubmissionDecision.blocked("TASK_SUBMISSION_MAX_REACHED",
                    "已达到最大提交次数");
        }

        // cap 校验：cap=0 不限制
        if (pointsCap != null && pointsCap > 0
                && earnedPoints >= pointsCap) {
            return ResubmissionDecision.blocked("TASK_SUBMISSION_POINTS_CAP_REACHED",
                    "已达到积分上限");
        }

        return ResubmissionDecision.allowed();
    }

    @Getter
    @RequiredArgsConstructor
    public static class ResubmissionDecision {
        private final boolean allowed;
        private final String blockCode;
        private final String blockMessage;

        public static ResubmissionDecision allowed() {
            return new ResubmissionDecision(true, null, null);
        }

        public static ResubmissionDecision blocked(String code, String message) {
            return new ResubmissionDecision(false, code, message);
        }
    }
}
