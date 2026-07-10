package com.cutegoals.common.config;

import com.cutegoals.common.CommonTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that prod profile overrides are applied on top of base config.
 */
@SpringBootTest(
    classes = CommonTestApplication.class,
    properties = "spring.profiles.active=prod"
)
class ConfigLoadingWithProdProfileTest {

    @Autowired
    private Environment env;

    @Test
    void prodProfileSetsEnvProperty() {
        assertThat(env.getProperty("app.env")).isEqualTo("prod");
    }

    @Test
    void prodProfileSetsWarnLogLevel() {
        assertThat(env.getProperty("logging.level.com.cutegoals")).isEqualTo("WARN");
    }

    @Test
    void baseConfigStillAccessible() {
        assertThat(env.getProperty("app.timezone")).isEqualTo("Asia/Shanghai");
        assertThat(env.getProperty("app.name")).isEqualTo("CuteGoals");
    }
}
