package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class ApiExecutorConfig {
    @Bean
    public Executor apiExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}
