package com.cutegoals.taskreview.it;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;

/**
 * Minimal Spring Boot application context for TaskReview integration tests.
 * Scans the full com.cutegoals package tree so that all mappers, services,
 * and the default InMemoryAuditService are discovered.
 *
 * <p>Redis auto-configuration is excluded — these tests run against H2 and
 * don't need a Redis server.
 */
@SpringBootApplication(
    scanBasePackages = "com.cutegoals",
    exclude = {
        RedisAutoConfiguration.class,
        RedisReactiveAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class
    }
)
@MapperScan({
    "com.cutegoals.auth.mapper",
    "com.cutegoals.task.mapper",
    "com.cutegoals.points.mapper",
    "com.cutegoals.taskreview.mapper"
})
public class TaskReviewTestApplication {
}
