-- V3__family_tables.sql
-- CuteGoals 2.0 家庭与成员表

-- 家庭表（单实例唯一约束通过应用层保证）
CREATE TABLE IF NOT EXISTS `family` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `name`          VARCHAR(100)    DEFAULT NULL             COMMENT '家庭名称',
    `avatar`        VARCHAR(500)    DEFAULT NULL             COMMENT '家庭头像',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭表';

-- 家庭成员表
CREATE TABLE IF NOT EXISTS `family_member` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`     BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `account_id`    BIGINT          NOT NULL                 COMMENT '账号 ID',
    `role`          VARCHAR(30)     NOT NULL                 COMMENT '角色：PARENT/CHILD',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_family_member` (`family_id`, `account_id`),
    KEY `idx_family_member_account` (`account_id`),
    KEY `idx_family_member_role` (`family_id`, `role`),
    CONSTRAINT `fk_family_member_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_family_member_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭成员表';

-- 孩子档案表
CREATE TABLE IF NOT EXISTS `child_profile` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`     BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `nickname`      VARCHAR(100)    NOT NULL                 COMMENT '昵称',
    `avatar`        VARCHAR(500)    DEFAULT NULL             COMMENT '头像 URL',
    `pin_hash`      VARCHAR(255)    DEFAULT NULL             COMMENT 'PIN 哈希',
    `birth_year`    INT             DEFAULT NULL             COMMENT '出生年份（可选）',
    `age_group`     VARCHAR(20)     DEFAULT NULL             COMMENT '年龄段（可选）',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE/DELETED',
    `deleted_at`    DATETIME        DEFAULT NULL             COMMENT '删除时间（匿名化）',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_child_family` (`family_id`),
    KEY `idx_child_status` (`family_id`, `status`),
    CONSTRAINT `fk_child_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='孩子档案表';

-- 家长邀请表
CREATE TABLE IF NOT EXISTS `parent_invitation` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`         BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `inviter_id`        BIGINT          NOT NULL                 COMMENT '邀请人账号 ID',
    `target_phone`      VARCHAR(20)     NOT NULL                 COMMENT '目标手机号',
    `secret_hash`       VARCHAR(255)    NOT NULL                 COMMENT '邀请秘密哈希',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/ACCEPTED/REJECTED/REVOKED/EXPIRED',
    `expires_at`        DATETIME        NOT NULL                 COMMENT '过期时间',
    `idempotency_key`   VARCHAR(128)    DEFAULT NULL             COMMENT '幂等键',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_invitation_family` (`family_id`),
    KEY `idx_invitation_target` (`target_phone`),
    KEY `idx_invitation_status` (`family_id`, `status`),
    KEY `idx_invitation_idempotency` (`inviter_id`, `idempotency_key`),
    CONSTRAINT `fk_invitation_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_invitation_inviter` FOREIGN KEY (`inviter_id`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家长邀请表';

-- 设备绑定表
CREATE TABLE IF NOT EXISTS `device_binding` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `family_id`         BIGINT          NOT NULL                 COMMENT '家庭 ID',
    `device_id`         VARCHAR(255)    NOT NULL                 COMMENT '设备标识',
    `credential_hash`   VARCHAR(255)    NOT NULL                 COMMENT '绑定凭据哈希（不可从 API 读取）',
    `status`            VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/REVOKED',
    `bound_by`          BIGINT          NOT NULL                 COMMENT '授权家长账号 ID',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_device_binding` (`family_id`, `device_id`),
    KEY `idx_device_binding_status` (`family_id`, `status`),
    CONSTRAINT `fk_device_family` FOREIGN KEY (`family_id`) REFERENCES `family` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_device_bound_by` FOREIGN KEY (`bound_by`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备绑定表';
