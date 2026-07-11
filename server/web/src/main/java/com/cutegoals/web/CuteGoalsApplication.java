package com.cutegoals.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CuteGoals 2.0 Server main application entry point.
 */
@SpringBootApplication(scanBasePackages = "com.cutegoals")
@MapperScan({
        "com.cutegoals.auth.mapper",
        "com.cutegoals.family.mapper",
        "com.cutegoals.task.mapper",
        "com.cutegoals.points.mapper",
        "com.cutegoals.taskreview.mapper",
        "com.cutegoals.prize.mapper",
        "com.cutegoals.exchange.mapper",
        "com.cutegoals.instancemanagement.mapper"
})
public class CuteGoalsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuteGoalsApplication.class, args);
    }
}
