package com.codewithkelvin.fx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class FxTradeLifecycleApplication {

    public static void main(String[] args) {
        SpringApplication.run(FxTradeLifecycleApplication.class, args);
    }
}
