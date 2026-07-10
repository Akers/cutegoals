-- V2__auth_tables.sql
-- CuteGoals 2.0 认证相关表
-- 🔒 安全敏感任务

-- 账号表
CREATE TABLE IF NOT EXISTS `account` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `phone`         VARCHAR(20)     NOT NULL                 COMMENT '手机号（唯一）',
    `password_hash` VARCHAR(255)    NOT NULL                 COMMENT '密码 bcrypt 哈希',
    `status`        VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE/DISABLED',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_account_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='账号表';

-- 角色绑定表
CREATE TABLE IF NOT EXISTS `role_binding` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `account_id`    BIGINT          NOT NULL                 COMMENT '账号 ID',
    `role`          VARCHAR(30)     NOT NULL                 COMMENT '角色：INSTANCE_ADMIN/PARENT/CHILD',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_binding_account_role` (`account_id`, `role`),
    KEY `idx_role_binding_account` (`account_id`),
    KEY `idx_role_binding_role` (`role`),
    CONSTRAINT `fk_role_binding_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色绑定表';

-- 初始化令牌表
CREATE TABLE IF NOT EXISTS `initialization_token` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `token_hash`    VARCHAR(255)    NOT NULL                 COMMENT '初始化令牌哈希',
    `consumed`      BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否已消费',
    `expires_at`    DATETIME        NOT NULL                 COMMENT '过期时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_init_token_hash` (`token_hash`),
    KEY `idx_init_token_consumed` (`token_hash`, `consumed`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='初始化令牌表';

-- 会话表
CREATE TABLE IF NOT EXISTS `session` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `session_id`        VARCHAR(128)    NOT NULL                 COMMENT '会话 ID（UUID）',
    `account_id`        BIGINT          NOT NULL                 COMMENT '账号 ID',
    `expires_at`        DATETIME        NOT NULL                 COMMENT '过期时间',
    `device_fingerprint` VARCHAR(255)   DEFAULT NULL             COMMENT '设备指纹',
    `revoked`           BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否已撤销',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_session_id` (`session_id`),
    KEY `idx_session_account` (`account_id`),
    KEY `idx_session_expires` (`expires_at`),
    CONSTRAINT `fk_session_account` FOREIGN KEY (`account_id`) REFERENCES `account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='会话表';

-- 刷新令牌表（支持家族链撤销）
CREATE TABLE IF NOT EXISTS `refresh_token` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `token_hash`    VARCHAR(255)    NOT NULL                 COMMENT '刷新令牌哈希',
    `session_id`    VARCHAR(128)    NOT NULL                 COMMENT '所属会话 ID',
    `family_id`     VARCHAR(128)    DEFAULT NULL             COMMENT '令牌家族链 ID（用于链式撤销）',
    `expires_at`    DATETIME        NOT NULL                 COMMENT '过期时间',
    `revoked`       BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否已撤销',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
    KEY `idx_refresh_token_session` (`session_id`),
    KEY `idx_refresh_token_family` (`family_id`),
    CONSTRAINT `fk_refresh_token_session` FOREIGN KEY (`session_id`) REFERENCES `session` (`session_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='刷新令牌表';

-- 登录限流表
CREATE TABLE IF NOT EXISTS `login_rate_limit` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `identifier`    VARCHAR(255)    NOT NULL                 COMMENT '限流标识（IP/手机号/账号）',
    `attempt_count` INT             NOT NULL DEFAULT 1       COMMENT '尝试次数',
    `window_start`  DATETIME        NOT NULL                 COMMENT '限流窗口起始时间',
    `locked_until`  DATETIME        DEFAULT NULL             COMMENT '锁定截止时间',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_rate_limit_identifier` (`identifier`),
    KEY `idx_rate_limit_window` (`identifier`, `window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录限流表';
