package com.cutegoals.taskreview.service;

import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskTemplate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResubmissionPolicyEvaluatorTest {

    private final ResubmissionPolicyEvaluator evaluator = new ResubmissionPolicyEvaluator();

    // ========== helpers ==========

    private TaskAssignment createAssignment(Boolean allowResubmit,
                                             Integer maxSubmissions,
                                             Integer pointsCap) {
        TaskAssignment a = new TaskAssignment();
        a.setSnapshotTemplateAllowResubmit(allowResubmit);
        a.setSnapshotTemplateMaxSubmissions(maxSubmissions);
        a.setSnapshotTemplatePointsCap(pointsCap);
        return a;
    }

    private TaskTemplate createTemplate(Boolean allowResubmit,
                                         Integer maxSubmissions,
                                         Integer pointsCap) {
        TaskTemplate t = new TaskTemplate();
        t.setAllowResubmit(allowResubmit);
        t.setMaxSubmissions(maxSubmissions);
        t.setPointsCap(pointsCap);
        return t;
    }

    // ========== allowResubmit == false / null → skip checks ==========

    @Test
    void allowResubmitFalse_shouldAllowRegardlessOfLimits() {
        TaskAssignment a = createAssignment(false, 1, 1);
        assertTrue(evaluator.evaluate(a, 1, 1).isAllowed());
    }

    @Test
    void allowResubmitNullWithoutTemplate_shouldAllow() {
        TaskAssignment a = createAssignment(null, null, null);
        assertTrue(evaluator.evaluate(a, 0, 0).isAllowed());
    }

    // ========== max / cap == 0 → no limit ==========

    @Test
    void maxZero_shouldAllow() {
        TaskAssignment a = createAssignment(true, 0, null);
        assertTrue(evaluator.evaluate(a, 100, 0).isAllowed());
    }

    @Test
    void capZero_shouldAllow() {
        TaskAssignment a = createAssignment(true, null, 0);
        assertTrue(evaluator.evaluate(a, 0, 100).isAllowed());
    }

    // ========== max 校验 ==========

    @Test
    void approvedCountBelowMax_shouldAllow() {
        TaskAssignment a = createAssignment(true, 5, null);
        assertTrue(evaluator.evaluate(a, 3, 0).isAllowed());
    }

    @Test
    void approvedCountEqualsMax_shouldBlockWithMaxReached() {
        TaskAssignment a = createAssignment(true, 5, null);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 5, 0);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_MAX_REACHED", d.getBlockCode());
    }

    @Test
    void approvedCountExceedsMax_shouldBlockWithMaxReached() {
        TaskAssignment a = createAssignment(true, 5, null);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 6, 0);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_MAX_REACHED", d.getBlockCode());
    }

    // ========== cap 校验 ==========

    @Test
    void earnedPointsBelowCap_shouldAllow() {
        TaskAssignment a = createAssignment(true, null, 100);
        assertTrue(evaluator.evaluate(a, 0, 95).isAllowed());
    }

    @Test
    void earnedPointsEqualsCap_shouldBlockWithCapReached() {
        TaskAssignment a = createAssignment(true, null, 100);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 0, 100);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_POINTS_CAP_REACHED", d.getBlockCode());
    }

    @Test
    void earnedPointsExceedsCap_shouldBlockWithCapReached() {
        TaskAssignment a = createAssignment(true, null, 100);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 0, 101);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_POINTS_CAP_REACHED", d.getBlockCode());
    }

    // ========== cap 边界值（需求 #7） ==========

    @Test
    void capBoundary_earned100EqualsCap100_shouldBlock() {
        TaskAssignment a = createAssignment(true, null, 100);
        // 95 earned + 5 new == 100 → blocked (>= cap)
        assertFalse(evaluator.evaluate(a, 0, 100).isAllowed());
    }

    @Test
    void capBoundary_earned105ExceedsCap100_shouldBlock() {
        TaskAssignment a = createAssignment(true, null, 100);
        // 95 earned + 10 new == 105 > 100 → blocked
        assertFalse(evaluator.evaluate(a, 0, 105).isAllowed());
    }

    // ========== NULL snapshot D9 回退 ==========

    @Test
    void nullSnapshot_withTemplate_fallbackBlocksByMax() {
        TaskAssignment a = createAssignment(null, null, null);
        TaskTemplate t = createTemplate(true, 3, 50);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 3, 0, t);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_MAX_REACHED", d.getBlockCode());
    }

    @Test
    void nullSnapshot_withTemplate_fallbackBlocksByCap() {
        TaskAssignment a = createAssignment(null, null, null);
        TaskTemplate t = createTemplate(true, 10, 50);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 0, 50, t);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_POINTS_CAP_REACHED", d.getBlockCode());
    }

    @Test
    void nullSnapshot_withTemplate_allowResubmitFalseFromTemplate() {
        TaskAssignment a = createAssignment(null, null, null);
        TaskTemplate t = createTemplate(false, 3, 50);
        assertTrue(evaluator.evaluate(a, 5, 60, t).isAllowed());
    }

    @Test
    void nullSnapshot_withoutTemplate_shouldAllow() {
        TaskAssignment a = createAssignment(null, null, null);
        assertTrue(evaluator.evaluate(a, 0, 0, (TaskTemplate) null).isAllowed());
    }

    // ========== max 优先于 cap（代码顺序） ==========

    @Test
    void maxBlocksBeforeCap_whenBothWouldBlock() {
        TaskAssignment a = createAssignment(true, 3, 100);
        ResubmissionPolicyEvaluator.ResubmissionDecision d = evaluator.evaluate(a, 3, 100);
        assertFalse(d.isAllowed());
        assertEquals("TASK_SUBMISSION_MAX_REACHED", d.getBlockCode());
    }

    // ========== 正常通过 ==========

    @Test
    void allChecksPass_shouldAllow() {
        TaskAssignment a = createAssignment(true, 10, 100);
        assertTrue(evaluator.evaluate(a, 3, 50).isAllowed());
    }

    @Test
    void allowResubmitTrueWithNoLimits_shouldAllow() {
        TaskAssignment a = createAssignment(true, null, null);
        assertTrue(evaluator.evaluate(a, 100, 1000).isAllowed());
    }
}
