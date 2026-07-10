-- V7__prize_blindbox_exchange_tables.sql
-- CuteGoals 2.0 奖品、盲盒与兑换表
-- ⚡ 并发/幂等/事务边界

-- 奖品表
CREATE TABLE IF NOT EXISTS `prize` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`     BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `name`          VARCHAR(100)    NOT NULL                 COMMENT '奖品名称',
    `description`   VARCHAR(2000)   DEFAULT NULL             COMMENT '奖品描述',
    `image`         VARCHAR(500)    DEFAULT NULL             COMMENT '奖品图片',
    `points_cost`   INT             NOT NULL                 COMMENT '积分价格（正整数）',
    `stock`         INT             NOT NULL DEFAULT 0       COMMENT '库存（非负整数）',
    `enabled`       BOOLEAN         NOT NULL DEFAULT TRUE    COMMENT '是否启用',
    `deleted`       BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否逻辑删除',
    `deleted_at`    DATETIME        DEFAULT NULL             COMMENT '删除时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_prize_family` (`family_id`),
    KEY `idx_prize_status` (`family_id`, `enabled`, `deleted`),
    CONSTRAINT `fk_prize_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='奖品表';

-- 盲盒奖池表
CREATE TABLE IF NOT EXISTS `blind_box_pool` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`             BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `name`                  VARCHAR(100)    NOT NULL                 COMMENT '奖池名称',
    `description`           VARCHAR(2000)   DEFAULT NULL             COMMENT '奖池描述',
    `cost_points`           INT             NOT NULL                 COMMENT '兑换所需积分（正整数）',
    `enabled`               BOOLEAN         NOT NULL DEFAULT TRUE    COMMENT '是否启用',
    `deleted`               BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否逻辑删除',
    `deleted_at`            DATETIME        DEFAULT NULL             COMMENT '删除时间',
    `availability_version`  VARCHAR(64)     DEFAULT NULL             COMMENT '可用性版本哈希（SHA-256）',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_blindbox_family` (`family_id`),
    KEY `idx_blindbox_status` (`family_id`, `enabled`, `deleted`),
    CONSTRAINT `fk_blindbox_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盲盒奖池表';

-- 盲盒奖品项表
CREATE TABLE IF NOT EXISTS `blind_box_item` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `pool_id`       BIGINT          NOT NULL                 COMMENT '奖池 ID',
    `prize_id`      BIGINT          NOT NULL                 COMMENT '奖品 ID',
    `weight`        INT             NOT NULL                 COMMENT '相对权重（正整数）',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_blindbox_item` (`pool_id`, `prize_id`),
    KEY `idx_blindbox_item_prize` (`prize_id`),
    CONSTRAINT `fk_blindbox_item_pool` FOREIGN KEY (`pool_id`) REFERENCES `blind_box_pool` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_blindbox_item_prize` FOREIGN KEY (`prize_id`) REFERENCES `prize` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='盲盒奖品项表';

-- 兑换记录表
CREATE TABLE IF NOT EXISTS `exchange` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `child_id`          BIGINT          NOT NULL                 COMMENT '孩子 ID',
    `family_id`         BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `type`              VARCHAR(10)     NOT NULL                 COMMENT '类型：DIRECT/BLIND_BOX',
    `status`            VARCHAR(30)     NOT NULL DEFAULT 'PENDING_FULFILLMENT' COMMENT '状态：PENDING_FULFILLMENT/FULFILLED/CANCELLED/REFUNDED',
    `cost_points`       INT             NOT NULL                 COMMENT '实际消耗积分',
    `idempotency_key`   VARCHAR(128)    NOT NULL                 COMMENT '幂等键',
    `prize_id`          BIGINT          DEFAULT NULL             COMMENT '奖品 ID（直接兑换）',
    `pool_id`           BIGINT          DEFAULT NULL             COMMENT '奖池 ID（盲盒兑换）',
    `result_prize_id`   BIGINT          DEFAULT NULL             COMMENT '抽中奖品 ID（盲盒）',
    `fulfilled_at`      DATETIME        DEFAULT NULL             COMMENT '兑现时间',
    `fulfilled_by`      BIGINT          DEFAULT NULL             COMMENT '兑现人',
    `cancelled_at`      DATETIME        DEFAULT NULL             COMMENT '取消时间',
    `cancelled_by`      BIGINT          DEFAULT NULL             COMMENT '取消人',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exchange_idempotency` (`child_id`, `idempotency_key`),
    KEY `idx_exchange_family` (`family_id`),
    KEY `idx_exchange_child` (`child_id`),
    KEY `idx_exchange_status` (`status`),
    KEY `idx_exchange_created` (`child_id`, `created_at`),
    CONSTRAINT `fk_exchange_child` FOREIGN KEY (`child_id`) REFERENCES `child_profile` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_exchange_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='兑换记录表';

-- 兑换快照表（不可变 - 无 updated_at）
CREATE TABLE IF NOT EXISTS `exchange_snapshot` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `exchange_id`           BIGINT          NOT NULL                 COMMENT '兑换 ID（一对一）',
    `prize_name`            VARCHAR(100)    DEFAULT NULL             COMMENT '奖品名称（兑换时）',
    `prize_image`           VARCHAR(500)    DEFAULT NULL             COMMENT '奖品图片（兑换时）',
    `prize_description`     VARCHAR(2000)   DEFAULT NULL             COMMENT '奖品描述（兑换时）',
    `points_cost`           INT             DEFAULT NULL             COMMENT '积分价格（兑换时）',
    `pool_name`             VARCHAR(100)    DEFAULT NULL             COMMENT '奖池名称（盲盒兑换时）',
    `pool_cost_points`      INT             DEFAULT NULL             COMMENT '奖池成本（盲盒兑换时）',
    `availability_version`  VARCHAR(64)     DEFAULT NULL             COMMENT '确认时的可用性版本',
    `candidate_probabilities` TEXT          DEFAULT NULL             COMMENT '候选集概率 JSON',
    `drawn_prize_name`      VARCHAR(100)    DEFAULT NULL             COMMENT '抽中奖品名称（盲盒）',
    `drawn_prize_image`     VARCHAR(500)    DEFAULT NULL             COMMENT '抽中奖品图片（盲盒）',
    `drawn_probability`     DECIMAL(5,2)    DEFAULT NULL             COMMENT '抽中概率（盲盒）',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_snapshot_exchange` (`exchange_id`),
    CONSTRAINT `fk_snapshot_exchange` FOREIGN KEY (`exchange_id`) REFERENCES `exchange` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='兑换快照表（不可变）';
