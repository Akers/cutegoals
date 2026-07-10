-- V6__points_tables.sql
-- CuteGoals 2.0 积分流水与余额表
-- ⚡ 并发/幂等/事务边界

-- 积分流水表（不可变 - 无 updated_at）
CREATE TABLE IF NOT EXISTS `points_ledger` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `child_id`          BIGINT          NOT NULL                 COMMENT '孩子 ID',
    `type`              VARCHAR(10)     NOT NULL                 COMMENT '类型：EARN/SPEND/REFUND/ADJUST',
    `amount`            INT             NOT NULL                 COMMENT '金额（正数增加余额，负数减少）',
    `balance_after`     INT             NOT NULL                 COMMENT '变动后余额',
    `business_ref`      VARCHAR(255)    NOT NULL                 COMMENT '唯一业务引用（来源类型+来源标识）',
    `source_snapshot`   TEXT            DEFAULT NULL             COMMENT '来源快照 JSON',
    `operator_id`       BIGINT          DEFAULT NULL             COMMENT '操作者账号 ID',
    `reason`            VARCHAR(500)    DEFAULT NULL             COMMENT '原因（调整必须填写）',
    `refund_source_id`  BIGINT          DEFAULT NULL             COMMENT '退款来源流水 ID（唯一约束）',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ledger_business_ref` (`child_id`, `business_ref`),
    UNIQUE KEY `uk_ledger_refund_source` (`refund_source_id`),
    KEY `idx_ledger_child` (`child_id`),
    KEY `idx_ledger_type` (`child_id`, `type`),
    KEY `idx_ledger_created` (`child_id`, `created_at`),
    KEY `idx_ledger_business_ref` (`business_ref`),
    CONSTRAINT `fk_ledger_child` FOREIGN KEY (`child_id`) REFERENCES `child_profile` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分流水表（不可变）';

-- 积分余额表（含乐观锁 version）
CREATE TABLE IF NOT EXISTS `points_balance` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `child_id`      BIGINT          NOT NULL                 COMMENT '孩子 ID（一对一）',
    `balance`       INT             NOT NULL DEFAULT 0       COMMENT '当前可用余额',
    `total_earned`  INT             NOT NULL DEFAULT 0       COMMENT '累计获得积分（仅 EARN 类型之和）',
    `version`       INT             NOT NULL DEFAULT 0       COMMENT '乐观锁版本号',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_balance_child` (`child_id`),
    CONSTRAINT `fk_balance_child` FOREIGN KEY (`child_id`) REFERENCES `child_profile` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='积分余额表';
