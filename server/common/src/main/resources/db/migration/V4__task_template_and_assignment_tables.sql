-- V4__task_template_and_assignment_tables.sql
-- CuteGoals 2.0 任务模板与分配表

-- 任务模板表
CREATE TABLE IF NOT EXISTS `task_template` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`     BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `name`          VARCHAR(100)    NOT NULL                 COMMENT '模板名称',
    `description`   VARCHAR(2000)   DEFAULT NULL             COMMENT '模板说明',
    `icon`          VARCHAR(500)    DEFAULT NULL             COMMENT '图标标识',
    `category`      VARCHAR(50)     NOT NULL                 COMMENT '分类',
    `version`       INT             NOT NULL DEFAULT 1       COMMENT '版本号（乐观锁）',
    `enabled`       BOOLEAN         NOT NULL DEFAULT TRUE    COMMENT '是否启用',
    `deleted`       BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否逻辑删除',
    `deleted_at`    DATETIME        DEFAULT NULL             COMMENT '删除时间',
    `deleted_by`    BIGINT          DEFAULT NULL             COMMENT '删除人',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_template_family` (`family_id`),
    KEY `idx_template_category` (`family_id`, `category`),
    KEY `idx_template_status` (`family_id`, `enabled`, `deleted`),
    CONSTRAINT `fk_template_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务模板表';

-- 任务难度表
CREATE TABLE IF NOT EXISTS `task_difficulty` (
    `id`             BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `template_id`    BIGINT          NOT NULL                 COMMENT '模板 ID',
    `name`           VARCHAR(50)     NOT NULL                 COMMENT '难度名称',
    `display_order`  INT             NOT NULL                 COMMENT '展示顺序',
    `reward_points`  INT             NOT NULL                 COMMENT '奖励积分（正整数）',
    `enabled`        BOOLEAN         NOT NULL DEFAULT TRUE    COMMENT '是否启用',
    `created_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_difficulty_template_name` (`template_id`, `name`),
    UNIQUE KEY `uk_difficulty_template_order` (`template_id`, `display_order`),
    KEY `idx_difficulty_template` (`template_id`),
    CONSTRAINT `fk_difficulty_template` FOREIGN KEY (`template_id`) REFERENCES `task_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务难度表';

-- 周期规则表
CREATE TABLE IF NOT EXISTS `task_recurrence_rule` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `template_id`       BIGINT          NOT NULL                 COMMENT '模板 ID（一对一）',
    `rule_type`         VARCHAR(20)     NOT NULL                 COMMENT '规则类型：DAILY/WEEKDAYS/WEEKENDS/CUSTOM_WEEKDAYS',
    `custom_weekdays`   VARCHAR(20)     DEFAULT NULL             COMMENT '自定义星期集合（如 1,3,5）',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_recurrence_template` (`template_id`),
    CONSTRAINT `fk_recurrence_template` FOREIGN KEY (`template_id`) REFERENCES `task_template` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周期规则表';

-- 任务分配表（含快照字段）
CREATE TABLE IF NOT EXISTS `task_assignment` (
    `id`                        BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`                 BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `template_id`               BIGINT          NOT NULL                 COMMENT '模板 ID',
    `child_id`                  BIGINT          NOT NULL                 COMMENT '孩子 ID',
    `difficulty_id`             BIGINT          DEFAULT NULL             COMMENT '难度 ID',
    `deadline`                  DATETIME        NOT NULL                 COMMENT '截止时间',
    `status`                    VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/SUBMITTED/APPROVED/REJECTED/COMPLETED',
    `late_policy`               VARCHAR(10)     NOT NULL DEFAULT 'REJECT' COMMENT '迟交策略：ALLOW/REJECT',
    `cancelled`                 BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否取消',
    `cancelled_at`              DATETIME        DEFAULT NULL             COMMENT '取消时间',
    `cancelled_reason`          VARCHAR(1000)   DEFAULT NULL             COMMENT '取消原因',
    `cancelled_by`              BIGINT          DEFAULT NULL             COMMENT '取消人',
    `version`                   INT             NOT NULL DEFAULT 1       COMMENT '版本号（乐观锁）',
    `idempotency_key`           VARCHAR(128)    DEFAULT NULL             COMMENT '幂等键',
    `occurrence_key`            VARCHAR(128)    DEFAULT NULL             COMMENT '周期发生键（家庭+孩子+模板+日期）',
    -- 快照字段：分配时固化模板与难度内容
    `snapshot_template_name`        VARCHAR(100)    NOT NULL             COMMENT '快照-模板名称',
    `snapshot_template_description` VARCHAR(2000)   DEFAULT NULL         COMMENT '快照-模板说明',
    `snapshot_template_category`    VARCHAR(50)     NOT NULL             COMMENT '快照-模板分类',
    `snapshot_template_icon`        VARCHAR(500)    DEFAULT NULL         COMMENT '快照-模板图标',
    `snapshot_difficulty_name`      VARCHAR(50)     NOT NULL             COMMENT '快照-难度名称',
    `snapshot_difficulty_reward`    INT             NOT NULL             COMMENT '快照-奖励积分',
    `created_at`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_assignment_occurrence` (`family_id`, `child_id`, `occurrence_key`),
    KEY `idx_assignment_family` (`family_id`),
    KEY `idx_assignment_child` (`child_id`),
    KEY `idx_assignment_status` (`status`),
    KEY `idx_assignment_deadline` (`deadline`),
    KEY `idx_assignment_idempotency` (`family_id`, `idempotency_key`),
    CONSTRAINT `fk_assignment_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_assignment_template` FOREIGN KEY (`template_id`) REFERENCES `task_template` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_assignment_child` FOREIGN KEY (`child_id`) REFERENCES `child_profile` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务分配表';

-- 任务分配快照表（历史版本追踪）
CREATE TABLE IF NOT EXISTS `task_assignment_snapshot` (
    `id`                        BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `assignment_id`             BIGINT          NOT NULL                 COMMENT '分配 ID',
    `template_name`             VARCHAR(100)    NOT NULL                 COMMENT '模板名称',
    `template_description`      VARCHAR(2000)   DEFAULT NULL             COMMENT '模板说明',
    `template_category`         VARCHAR(50)     NOT NULL                 COMMENT '模板分类',
    `template_icon`             VARCHAR(500)    DEFAULT NULL             COMMENT '模板图标',
    `difficulty_name`           VARCHAR(50)     NOT NULL                 COMMENT '难度名称',
    `difficulty_reward_points`  INT             NOT NULL                 COMMENT '奖励积分',
    `created_at`                DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_snapshot_assignment` (`assignment_id`),
    CONSTRAINT `fk_snapshot_assignment` FOREIGN KEY (`assignment_id`) REFERENCES `task_assignment` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务分配快照表';
