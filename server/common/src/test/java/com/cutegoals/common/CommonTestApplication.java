package com.cutegoals.common;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

/**
 * Minimal Spring Boot application class for testing the common module.
 * Scans the com.cutegoals.common package for configuration classes.
 * Flyway auto-configuration is excluded here because the migration scripts are
 * MySQL-flavoured and are validated by FlywayMigrationTest using programmatic
 * Flyway against H2 in MySQL mode; Spring Boot tests that do not need the
 * database should not trigger auto-migration.
 */
@SpringBootApplication(
    scanBasePackages = "com.cutegoals.common",
    exclude = FlywayAutoConfiguration.class
)
public class CommonTestApplication {
}
