package com.cutegoals.auth.bootstrap;

import com.cutegoals.auth.mapper.InitializationTokenMapper;
import com.cutegoals.auth.service.InitializationTokenService;
import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.entity.auth.InitializationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ApplicationRunner that bootstraps the initialization_token table on startup.
 * <p>
 * If the table is already populated (any row exists), this runner does nothing.
 * Otherwise, it seeds the table using the {@code INIT_TOKEN} environment variable,
 * or auto-generates a token in development mode.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InitializationBootstrap implements ApplicationRunner {

    private final InitializationTokenMapper tokenMapper;
    private final InitializationTokenService tokenService;

    @Value("${INIT_TOKEN:}")
    private String initToken;

    @Value("${app.production:false}")
    private boolean production;

    @Override
    public void run(ApplicationArguments args) {
        // If the table already has any records (regardless of consumed/expired status),
        // the bootstrap has already run before — skip.
        long total = tokenMapper.selectCount(null);
        if (total > 0) {
            log.debug("InitializationTokenBootstrap: token table not empty (count={}), skip", total);
            return;
        }

        // Table is empty — seed the first initialization token.
        if (initToken != null && !initToken.isBlank()) {
            // Hash the value from INIT_TOKEN env var and insert an unconsumed record.
            String hash = InitializationTokenService.hashToken(initToken);
            InitializationToken entity = new InitializationToken();
            entity.setTokenHash(hash);
            entity.setConsumed(false);
            entity.setExpiresAt(LocalDateTime.now().plusHours(AuthConstants.INIT_TOKEN_VALIDITY_HOURS));
            tokenMapper.insert(entity);
            log.info("InitializationTokenBootstrap: 从 INIT_TOKEN 环境变量写入初始化令牌（有效期 {} 小时）",
                    AuthConstants.INIT_TOKEN_VALIDITY_HOURS);
            log.info("InitializationTokenBootstrap: 请在 /admin/init 页面使用 INIT_TOKEN 的原始值完成初始化");
        } else if (!production) {
            // Development mode: auto-generate a token and print the plaintext to logs.
            String plain = tokenService.generateToken();
            log.warn("InitializationTokenBootstrap: 开发模式下自动生成初始化令牌（未配置 INIT_TOKEN 环境变量）");
            log.warn("InitializationTokenBootstrap: 初始化令牌（仅显示一次）: {}", plain);
            log.warn("InitializationTokenBootstrap: 请在 /admin/init 页面使用此令牌完成初始化");
        } else {
            // Production mode with no INIT_TOKEN configured — fatal configuration error.
            log.error("InitializationTokenBootstrap: 生产模式下必须配置 INIT_TOKEN 环境变量，否则无法初始化系统");
        }
    }
}
