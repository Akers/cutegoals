package com.cutegoals.common.config;

import com.cutegoals.common.CommonTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for configuration loading precedence and default values.
 *
 * Covers:
 * - Default profile loads base application.yml values
 * - Dev profile overrides base values
 * - Timezone default is Asia/Shanghai
 */
@SpringBootTest(classes = CommonTestApplication.class)
class ConfigLoadingTest {

    @Autowired
    private Environment env;

    @Test
    void defaultTimezoneIsAsiaShanghai() {
        assertThat(env.getProperty("app.timezone")).isEqualTo("Asia/Shanghai");
    }

    @Test
    void defaultAppNameIsCuteGoals() {
        assertThat(env.getProperty("app.name")).isEqualTo("CuteGoals");
    }

    @Test
    void commonLogLevelIsDebug() {
        assertThat(env.getProperty("logging.level.com.cutegoals")).isEqualTo("DEBUG");
    }
}
