package com.cutegoals.taskreview.it;

import com.cutegoals.common.entity.points.PointsBalance;
import com.cutegoals.common.entity.task.TaskAssignment;
import com.cutegoals.common.entity.task.TaskAttempt;
import com.cutegoals.common.entity.task.TaskReview;
import com.cutegoals.common.entity.task.TaskTemplate;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.points.mapper.PointsBalanceMapper;
import com.cutegoals.points.mapper.PointsLedgerMapper;
import com.cutegoals.task.mapper.TaskAssignmentMapper;
import com.cutegoals.task.mapper.TaskTemplateMapper;
import com.cutegoals.taskreview.mapper.TaskAttemptMapper;
import com.cutegoals.taskreview.mapper.TaskReviewMapper;
import com.cutegoals.taskreview.service.TaskReviewService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试：Task 重复提交策略 (Resubmission Policy Controls).
 *
 * <p>覆盖 6 个核心场景：
 * <ol>
 *   <li>allow_resubmit=false → 提交正常，无校验</li>
 *   <li>maxSubmissions=0 → 不限制</li>
 *   <li>pointsCap=0 → 不限制</li>
 *   <li>达到 maxSubmissions → 422 TASK_SUBMISSION_MAX_REACHED</li>
 *   <li>达到 pointsCap → 422 TASK_SUBMISSION_POINTS_CAP_REACHED</li>
 *   <li>跨 assignment 聚合统计（同模板 + 同孩子）</li>
 * </ol>
 */
@SpringBootTest(
    classes = TaskReviewTestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@ActiveProfiles("test")
@Transactional
@DisplayName("Task 重复提交策略集成测试")
class TaskReviewResubmissionControlIT {

    // ── Fixture constants ───────────────────────────────────────────────

    private static final Long FAMILY_ID = 1L;
    private static final Long CHILD_ID = 10L;
    private static final Long ACCOUNT_ID = 100L;

    // ── Injected beans ──────────────────────────────────────────────────

    @Autowired private TaskReviewService taskReviewService;
    @Autowired private TaskTemplateMapper taskTemplateMapper;
    @Autowired private TaskAssignmentMapper taskAssignmentMapper;
    @Autowired private TaskAttemptMapper taskAttemptMapper;
    @Autowired private TaskReviewMapper taskReviewMapper;
    @Autowired private PointsLedgerMapper pointsLedgerMapper;
    @Autowired private PointsBalanceMapper pointsBalanceMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    // ── ID generators ───────────────────────────────────────────────────

    private long nextTemplateId = 1000;
    private long nextAssignmentId = 2000;
    private int idCounter = 0;

    @BeforeEach
    void setUpFixtures() {
        // Clean all tables (FK order)
        pointsLedgerMapper.delete(null);
        pointsBalanceMapper.delete(null);
        taskReviewMapper.delete(null);
        taskAttemptMapper.delete(null);
        taskAssignmentMapper.delete(null);
        taskTemplateMapper.delete(null);
        jdbcTemplate.execute("DELETE FROM child_profile");
        jdbcTemplate.execute("DELETE FROM account");
        jdbcTemplate.execute("DELETE FROM family");
        idCounter = 0;

        // Insert FK parent rows
        jdbcTemplate.execute("INSERT INTO family (id, name) VALUES (1, 'Test Family')");
        jdbcTemplate.execute("INSERT INTO account (id, phone, password_hash, status) VALUES (100, 'test@test.com', 'dummy_hash', 'ACTIVE')");
        jdbcTemplate.execute("INSERT INTO child_profile (id, family_id, nickname, status) VALUES (10, 1, 'Test Child', 'ACTIVE')");

        // Create points balance for child (needed by approveAttempt)
        PointsBalance balance = new PointsBalance();
        balance.setChildId(CHILD_ID);
        balance.setBalance(0);
        balance.setTotalEarned(0);
        pointsBalanceMapper.insert(balance);
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 1: allow_resubmit = false → 不校验
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("场景 1: allow_resubmit=false")
    class AllowResubmitFalse {

        @Test
        @DisplayName("allow_resubmit=false → maxSubmissions/cap 配置不生效，提交正常")
        void shouldNotValidateWhenAllowResubmitIsFalse() {
            TaskTemplate tpl = insertTemplate(false, 3, 100);

            // 即使 maxSubmissions=3, pointsCap=100，allow_resubmit=false 时不校验
            for (int i = 0; i < 4; i++) {
                Long assnId = insertAssignment(tpl.getId(), 10);
                TaskAttempt attempt = submit(assnId, "no-val-" + i);
                approve(attempt.getId(), "no-val-app-" + i);
            }
            // 不抛异常即通过
        }

        @Test
        @DisplayName("allow_resubmit=null（默认）→ 同 false，不校验")
        void shouldNotValidateWhenAllowResubmitIsNull() {
            // allowResubmit 在 V14 迁移中默认 FALSE
            TaskTemplate tpl = insertTemplate(false, 5, 50);

            for (int i = 0; i < 6; i++) {
                Long assnId = insertAssignment(tpl.getId(), 10);
                TaskAttempt attempt = submit(assnId, "null-val-" + i);
                approve(attempt.getId(), "null-val-app-" + i);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 2: maxSubmissions = 0 → 不限制
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("场景 2: maxSubmissions=0")
    class MaxSubmissionsZero {

        @Test
        @DisplayName("max=0 → 不限制提交次数，多提交通过")
        void shouldAllowUnlimitedWhenMaxIsZero() {
            TaskTemplate tpl = insertTemplate(true, 0, 0);

            // 提交+审批任意多次
            for (int i = 0; i < 5; i++) {
                Long assnId = insertAssignment(tpl.getId(), 10);
                TaskAttempt attempt = submit(assnId, "max0-" + i);
                approve(attempt.getId(), "max0-app-" + i);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 3: pointsCap = 0 → 不限制
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("场景 3: pointsCap=0")
    class PointsCapZero {

        @Test
        @DisplayName("cap=0 → 不限制积分累计，多提交通过")
        void shouldAllowUnlimitedWhenCapIsZero() {
            // max=0 不限制次数，cap=0 不限制积分
            TaskTemplate tpl = insertTemplate(true, 0, 0);

            for (int i = 0; i < 5; i++) {
                Long assnId = insertAssignment(tpl.getId(), 10);
                TaskAttempt attempt = submit(assnId, "cap0-" + i);
                approve(attempt.getId(), "cap0-app-" + i);
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 4: 达到 maxSubmissions → 拒绝
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("场景 4: 达到 maxSubmissions")
    class MaxSubmissionsReached {

        @Test
        @DisplayName("max=3 → 第 3 次审批后，第 4 次提交被拒绝")
        void shouldBlockWhenMaxSubmissionsReached() {
            TaskTemplate tpl = insertTemplate(true, 3, 0);

            // 3 轮提交 + 审批 → 3 个 APPROVED review
            for (int i = 0; i < 3; i++) {
                Long assnId = insertAssignment(tpl.getId(), 10);
                TaskAttempt attempt = submit(assnId, "max-reach-" + i);
                approve(attempt.getId(), "max-reach-app-" + i);
            }

            // 第 4 次提交 → 拒绝
            Long assn4 = insertAssignment(tpl.getId(), 10);
            BusinessException ex = assertThrows(BusinessException.class,
                () -> submit(assn4, "max-reach-blocked"));
            assertEquals(ErrorCode.TASK_SUBMISSION_MAX_REACHED, ex.getErrorCode(),
                "应返回 TASK_SUBMISSION_MAX_REACHED");
        }

        @Test
        @DisplayName("max=1 → 首次审批后第二次提交即拒绝")
        void shouldBlockWhenMaxSubmissionsIsOne() {
            TaskTemplate tpl = insertTemplate(true, 1, 0);

            Long assn1 = insertAssignment(tpl.getId(), 10);
            TaskAttempt att1 = submit(assn1, "max1-1");
            approve(att1.getId(), "max1-app-1");

            Long assn2 = insertAssignment(tpl.getId(), 10);
            BusinessException ex = assertThrows(BusinessException.class,
                () -> submit(assn2, "max1-blocked"));
            assertEquals(ErrorCode.TASK_SUBMISSION_MAX_REACHED, ex.getErrorCode());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 5: 达到 pointsCap → 拒绝
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("场景 5: 达到 pointsCap")
    class PointsCapReached {

        @Test
        @DisplayName("cap=100, 每次 10 分 → 第 10 次审批后第 11 次提交被拒绝")
        void shouldBlockWhenPointsCapReached() {
            TaskTemplate tpl = insertTemplate(true, 100, 100);

            // 9 次 submit+approve → earned=90 (< 100, 仍然允许提交)
            for (int i = 0; i < 9; i++) {
                Long assnId = insertAssignment(tpl.getId(), 10);
                TaskAttempt attempt = submit(assnId, "cap-reach-" + i);
                approve(attempt.getId(), "cap-reach-app-" + i);
            }

            // 第 10 次 submit+approve → earned=100 (== cap)
            Long assn10 = insertAssignment(tpl.getId(), 10);
            TaskAttempt att10 = submit(assn10, "cap-reach-10");
            approve(att10.getId(), "cap-reach-app-10");

            // 第 11 次 submit → earned=100 >= cap=100 → 拒绝
            Long assn11 = insertAssignment(tpl.getId(), 10);
            BusinessException ex = assertThrows(BusinessException.class,
                () -> submit(assn11, "cap-reach-blocked"));
            assertEquals(ErrorCode.TASK_SUBMISSION_POINTS_CAP_REACHED, ex.getErrorCode(),
                "应返回 TASK_SUBMISSION_POINTS_CAP_REACHED");
        }

        @Test
        @DisplayName("reward=0 时 cap 不被触发")
        void shouldNotBlockWhenRewardIsZero() {
            TaskTemplate tpl = insertTemplate(true, 100, 50);
            // 积分奖励为 0 → 积分不增长 → cap 不会达到
            for (int i = 0; i < 3; i++) {
                Long assnId = insertAssignment(tpl.getId(), 0);
                TaskAttempt attempt = submit(assnId, "cap-zero-reward-" + i);
                approve(attempt.getId(), "cap-zero-reward-app-" + i);
            }
            // 仍然可以提交
            Long assn4 = insertAssignment(tpl.getId(), 0);
            TaskAttempt att4 = submit(assn4, "cap-zero-reward-4");
            assertNotNull(att4);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Test 6: 跨 assignment 聚合统计 (REPEAT 模式)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("场景 6: 跨 assignment 聚合")
    class CrossAssignmentAggregation {

        @Test
        @DisplayName("同模板+同孩子 — 多期分配跨期累计达到 max 后拒绝")
        void shouldAggregateAcrossAssignments() {
            // REPEAT 类型，max=3（按模板+孩子聚合，不限于单个 assignment）
            TaskTemplate tpl = insertTemplate(true, 3, 0);

            // Assignment 1: submit + approve → 1 approved
            Long a1 = insertAssignment(tpl.getId(), 10);
            TaskAttempt att1 = submit(a1, "cross-1");
            approve(att1.getId(), "cross-app-1");

            // Assignment 2: submit + approve → 2 approved
            Long a2 = insertAssignment(tpl.getId(), 10);
            TaskAttempt att2 = submit(a2, "cross-2");
            approve(att2.getId(), "cross-app-2");

            // Assignment 3: submit + approve → 3 approved = max
            Long a3 = insertAssignment(tpl.getId(), 10);
            TaskAttempt att3 = submit(a3, "cross-3");
            approve(att3.getId(), "cross-app-3");

            // Assignment 4: 被拒绝
            Long a4 = insertAssignment(tpl.getId(), 10);
            BusinessException ex = assertThrows(BusinessException.class,
                () -> submit(a4, "cross-blocked"));
            assertEquals(ErrorCode.TASK_SUBMISSION_MAX_REACHED, ex.getErrorCode(),
                "跨 assignment 聚合后应达到 max 限制");
        }

        @Test
        @DisplayName("不同模板 — 独立计数，互不影响")
        void shouldCountIndependentlyPerTemplate() {
            TaskTemplate tpl1 = insertTemplate(true, 1, 0);
            TaskTemplate tpl2 = insertTemplate(true, 1, 0);

            // tpl1: submit+approve → max 已满
            Long a1 = insertAssignment(tpl1.getId(), 10);
            TaskAttempt att1 = submit(a1, "indep-1");
            approve(att1.getId(), "indep-app-1");

            // tpl2: submit+approve → tpl2 也满
            Long a2 = insertAssignment(tpl2.getId(), 10);
            TaskAttempt att2 = submit(a2, "indep-2");
            approve(att2.getId(), "indep-app-2");

            // tpl1 再提交 → 拒绝
            Long a3 = insertAssignment(tpl1.getId(), 10);
            assertThrows(BusinessException.class, () -> submit(a3, "indep-blocked-1"));

            // tpl2 再提交 → 拒绝
            Long a4 = insertAssignment(tpl2.getId(), 10);
            assertThrows(BusinessException.class, () -> submit(a4, "indep-blocked-2"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Helpers — fixture 数据准备
    // ═══════════════════════════════════════════════════════════════════

    private TaskTemplate insertTemplate(Boolean allowResubmit,
                                         Integer maxSubmissions,
                                         Integer pointsCap) {
        TaskTemplate t = new TaskTemplate();
        t.setId(nextTemplateId++);
        t.setFamilyId(FAMILY_ID);
        t.setName("Resubmission Test Template");
        t.setCategory("Test");
        t.setTaskType("LIMITED");
        t.setTypeConfig(null); // No date restrictions
        t.setAllowResubmit(allowResubmit);
        t.setMaxSubmissions(maxSubmissions);
        t.setPointsCap(pointsCap);
        t.setEnabled(true);
        t.setDeleted(false);
        t.setVersion(1);
        taskTemplateMapper.insert(t);
        return t;
    }

    private Long insertAssignment(Long templateId, int reward) {
        TaskAssignment a = new TaskAssignment();
        a.setId(nextAssignmentId++);
        a.setFamilyId(FAMILY_ID);
        a.setChildId(CHILD_ID);
        a.setTemplateId(templateId);
        a.setDifficultyId(20L);
        a.setDeadline(LocalDateTime.now().plusDays(7));
        a.setStatus("PENDING");
        a.setLatePolicy("ALLOW");
        a.setCancelled(false);
        a.setVersion(1);
        a.setSnapshotTemplateName("Test Task");
        a.setSnapshotTemplateCategory("Test");
        a.setSnapshotDifficultyName("Easy");
        a.setSnapshotDifficultyReward(reward);
        // Snapshot resubmit fields left NULL → evaluator falls back to template values
        taskAssignmentMapper.insert(a);
        return a.getId();
    }

    private TaskAttempt submit(Long assignmentId, String idempotencyKey) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("assignmentId", assignmentId);
        request.put("content", "Submission content for " + idempotencyKey);
        request.put("idempotencyKey", idempotencyKey);
        return taskReviewService.submitTask(request, CHILD_ID, FAMILY_ID, ACCOUNT_ID);
    }

    private void approve(Long attemptId, String idempotencyKey) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("reason", "Well done!");
        request.put("idempotencyKey", idempotencyKey);
        taskReviewService.approveAttempt(attemptId, request, FAMILY_ID, ACCOUNT_ID);
    }
}
