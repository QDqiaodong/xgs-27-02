package com.example.scriptreview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScriptReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScriptReviewApplication.class, args);
    }
}