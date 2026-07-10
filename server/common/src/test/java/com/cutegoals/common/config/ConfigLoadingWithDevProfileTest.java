package com.cutegoals.common.config;

import com.cutegoals.common.CommonTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that dev profile overrides are applied on top of base config.
 */
@SpringBootTest(
    classes = CommonTestApplication.class,
    properties = "spring.profiles.active=dev"
)
class ConfigLoadingWithDevProfileTest {

    @Autowired
    private Environment env;

    @Test
    void devProfileSetsEnvProperty() {
        assertThat(env.getProperty("app.env")).isEqualTo("dev");
    }

    @Test
    void baseConfigStillAccessible() {
        assertThat(env.getProperty("app.timezone")).isEqualTo("Asia/Shanghai");
        assertThat(env.getProperty("app.name")).isEqualTo("CuteGoals");
    }
}
