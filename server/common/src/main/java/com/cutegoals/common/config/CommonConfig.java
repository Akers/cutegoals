package com.cutegoals.common.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Common application configuration.
 *
 * Sets the default JVM timezone from application config (default: Asia/Shanghai).
 * Exposes app name and timezone properties as injectable beans.
 */
@Configuration
public class CommonConfig {

    private static final Logger log = LoggerFactory.getLogger(CommonConfig.class);

    @Value("${app.timezone:Asia/Shanghai}")
    private String timezone;

    @Value("${app.name:CuteGoals}")
    private String appName;

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone(timezone));
        log.info("App [{}] initialized with timezone: {}", appName, timezone);
    }

    public String getTimezone() {
        return timezone;
    }

    public String getAppName() {
        return appName;
    }
}
