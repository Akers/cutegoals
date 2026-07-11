package com.cutegoals.web.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Startup configuration validator (Task 8.4).
 * Checks that all required secrets are non-empty and not default/example values.
 * Fails fast with CONFIG_INVALID if validation fails.
 */
@Component
public class ConfigValidator {

    private static final Logger log = LoggerFactory.getLogger(ConfigValidator.class);

    @Value("${app.production:false}")
    private boolean production;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${app.auth.jwt.secret:}")
    private String jwtSecret;

    @Value("${INIT_TOKEN:}")
    private String initToken;

    @Value("${CUTEGOALS_JWT_SECRET:}")
    private String cutegoalsJwtSecret;

    private static final List<String> EXAMPLE_PASSWORDS = List.of(
            "changeit", "password", "changeme", "secret", "your-password",
            "your_secret_key_here", "your-strong-password-here"
    );

    @PostConstruct
    public void validate() {
        if (!production) {
            log.info("ConfigValidator: 开发模式，跳过严格配置校验");
            log.info("ConfigValidator: 此模式不可用于生产环境");
            return;
        }

        log.info("ConfigValidator: 开始校验生产配置...");
        List<String> errors = new ArrayList<>();

        // Check PostgreSQL password
        if (isEmpty(dbPassword)) {
            errors.add("PG_PASSWORD/spring.datasource.password");
        } else if (isExampleValue(dbPassword)) {
            errors.add("PG_PASSWORD/spring.datasource.password");
        }

        // Check Redis password
        if (isEmpty(redisPassword)) {
            errors.add("REDIS_PASSWORD/spring.data.redis.password");
        } else if (isExampleValue(redisPassword)) {
            errors.add("REDIS_PASSWORD/spring.data.redis.password");
        }

        // Check JWT secret
        String jwt = jwtSecret;
        if (isEmpty(jwt)) { jwt = cutegoalsJwtSecret; }
        if (isEmpty(jwt)) {
            errors.add("JWT_SECRET/app.auth.jwt.secret");
        } else if (isExampleValue(jwt) || jwt.length() < 32) {
            errors.add("JWT_SECRET/app.auth.jwt.secret");
        }

        if (!errors.isEmpty()) {
            log.error("========================================");
            log.error("  CONFIG_INVALID: 生产配置校验失败");
            log.error("========================================");
            log.error("  以下配置项为空或为示例值：");
            for (String field : errors) {
                log.error("    - {}", field);
            }
            log.error("");
            log.error("  请检查 .env 文件并设置强密码/密钥。");
            log.error("  不要在生产环境使用默认值或示例值。");
            log.error("========================================");

            throw new IllegalStateException(
                    "CONFIG_INVALID: " + errors.size() + " 个配置项校验失败。受影响字段: " +
                    String.join(", ", errors) +
                    "。详情请查看日志。"
            );
        }

        log.info("ConfigValidator: ✓ 生产配置校验通过");
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private boolean isExampleValue(String value) {
        if (value == null) return true;
        String lower = value.toLowerCase().trim();
        for (String example : EXAMPLE_PASSWORDS) {
            if (lower.contains(example)) {
                return true;
            }
        }
        return false;
    }
}
