package com.cutegoals.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * CuteGoals 2.0 Server main application entry point.
 */
@SpringBootApplication(scanBasePackages = "com.cutegoals")
public class CuteGoalsApplication {

    public static void main(String[] args) {
        SpringApplication.run(CuteGoalsApplication.class, args);
    }
}
