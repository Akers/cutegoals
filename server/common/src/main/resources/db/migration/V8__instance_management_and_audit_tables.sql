-- V8__instance_management_and_audit_tables.sql
-- CuteGoals 2.0 实例管理与审计表
-- 🔒 安全敏感任务

-- 实例配置表
CREATE TABLE IF NOT EXISTS `instance_config` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `config_key`    VARCHAR(100)    NOT NULL                 COMMENT '配置键',
    `config_value`  TEXT            DEFAULT NULL             COMMENT '配置值（秘密字段以掩码存储）',
    `masked`        BOOLEAN         NOT NULL DEFAULT FALSE   COMMENT '是否为脱敏秘密字段',
    `description`   VARCHAR(500)    DEFAULT NULL             COMMENT '配置说明',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='实例配置表';

-- 审计日志表
CREATE TABLE IF NOT EXISTS `audit_log` (
    `id`            BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `actor_id`      BIGINT          DEFAULT NULL             COMMENT '操作者 ID',
    `actor_type`    VARCHAR(20)     DEFAULT NULL             COMMENT '操作者类型',
    `event_type`    VARCHAR(50)     NOT NULL                 COMMENT '事件类型',
    `result`        VARCHAR(10)     NOT NULL                 COMMENT '结果：SUCCESS/FAILURE',
    `object_type`   VARCHAR(50)     DEFAULT NULL             COMMENT '对象类型',
    `object_id`     VARCHAR(100)    DEFAULT NULL             COMMENT '对象标识',
    `summary`       VARCHAR(2000)   DEFAULT NULL             COMMENT '变更摘要（脱敏）',
    `request_id`    VARCHAR(128)    DEFAULT NULL             COMMENT '请求关联标识',
    `metadata`      TEXT            DEFAULT NULL             COMMENT '额外元数据 JSON',
    `created_at`    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_audit_actor_event` (`actor_id`, `event_type`, `created_at`),
    KEY `idx_audit_event_time` (`event_type`, `created_at`),
    KEY `idx_audit_object` (`object_type`, `object_id`),
    KEY `idx_audit_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表';

-- 备份运行记录表
CREATE TABLE IF NOT EXISTS `backup_run` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `started_at`        DATETIME        NOT NULL                 COMMENT '备份开始时间',
    `completed_at`      DATETIME        DEFAULT NULL             COMMENT '备份完成时间',
    `status`            VARCHAR(20)     NOT NULL                 COMMENT '状态：RUNNING/SUCCESS/FAILED',
    `size_bytes`        BIGINT          DEFAULT NULL             COMMENT '备份大小（字节）',
    `checksum`          VARCHAR(64)     DEFAULT NULL             COMMENT '备份校验和',
    `app_version`       VARCHAR(50)     DEFAULT NULL             COMMENT '应用版本',
    `schema_version`    VARCHAR(50)     DEFAULT NULL             COMMENT '数据库 schema 版本',
    `error_message`     VARCHAR(2000)   DEFAULT NULL             COMMENT '错误信息',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_backup_status` (`status`),
    KEY `idx_backup_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='备份运行记录表';

-- 恢复演练结果表
CREATE TABLE IF NOT EXISTS `recovery_drill` (
    `id`                BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键',
    `started_at`        DATETIME        NOT NULL                 COMMENT '演练开始时间',
    `completed_at`      DATETIME        DEFAULT NULL             COMMENT '演练完成时间',
    `success`           BOOLEAN         NOT NULL                 COMMENT '是否成功',
    `rpo_seconds`       INT             DEFAULT NULL             COMMENT '实际 RPO（秒）',
    `rto_seconds`       INT             DEFAULT NULL             COMMENT '实际 RTO（秒）',
    `backup_used`       VARCHAR(255)    DEFAULT NULL             COMMENT '使用的备份标识',
    `details`           TEXT            DEFAULT NULL             COMMENT '演练详情',
    `created_at`        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_drill_success` (`success`),
    KEY `idx_drill_started` (`started_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='恢复演练结果表';
