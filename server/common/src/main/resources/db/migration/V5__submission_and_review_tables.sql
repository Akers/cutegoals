-- V5__submission_and_review_tables.sql
-- CuteGoals 2.0 提交与审核表
-- 不可变表（无 updated_at）

-- 任务提交尝试表（不可变）
CREATE TABLE IF NOT EXISTS `task_attempt` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `assignment_id`         BIGINT          NOT NULL                 COMMENT '分配 ID',
    `child_id`              BIGINT          NOT NULL                 COMMENT '孩子 ID',
    `attempt_number`        INT             NOT NULL                 COMMENT '尝试序号（按孩子+分配递增）',
    `content`               VARCHAR(2000)   DEFAULT NULL             COMMENT '提交说明',
    `attachments`           TEXT            DEFAULT NULL             COMMENT '佐证引用 JSON（最多 10 个）',
    `submitted_at`          DATETIME        NOT NULL                 COMMENT '提交时间',
    `is_late`               BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否迟交',
    `late_policy_applied`   VARCHAR(10)     NOT NULL                 COMMENT '当时有效的迟交策略',
    `idempotency_key`       VARCHAR(128)    DEFAULT NULL             COMMENT '请求幂等键',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_attempt_assignment_seq` (`assignment_id`, `child_id`, `attempt_number`),
    UNIQUE KEY `uk_attempt_idempotency` (`child_id`, `idempotency_key`),
    KEY `idx_attempt_assignment` (`assignment_id`),
    KEY `idx_attempt_child` (`child_id`),
    CONSTRAINT `fk_attempt_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `task_assignment` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_attempt_child` FOREIGN KEY (`child_id`) REFERENCES `child_profile` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务提交尝试表（不可变）';

-- 任务审核记录表（不可变）
CREATE TABLE IF NOT EXISTS `task_review` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `attempt_id`        BIGINT          NOT NULL                 COMMENT '提交尝试 ID',
    `assignment_id`     BIGINT          NOT NULL                 COMMENT '分配 ID',
    `reviewer_id`       BIGINT          NOT NULL                 COMMENT '审核人账号 ID',
    `decision`          VARCHAR(10)     NOT NULL                 COMMENT '决定：APPROVED/REJECTED',
    `reason`            VARCHAR(1000)   DEFAULT NULL             COMMENT '审核原因',
    `idempotency_key`   VARCHAR(128)    DEFAULT NULL             COMMENT '审核请求幂等键',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_attempt` (`attempt_id`),
    KEY `idx_review_assignment` (`assignment_id`),
    KEY `idx_review_reviewer` (`reviewer_id`),
    KEY `idx_review_decision` (`decision`),
    CONSTRAINT `fk_review_attempt` FOREIGN KEY (`attempt_id`) REFERENCES `task_attempt` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `task_assignment` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_review_reviewer` FOREIGN KEY (`reviewer_id`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务审核记录表（不可变）';
