package com.laa.llm.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class LlmConfig {

    /** Virtual threads for provider calls, so the gateway can enforce timeouts around blocking SDK calls. */
    @Bean(destroyMethod = "close")
    ExecutorService llmExecutor() {
        return Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("llm-", 0).factory());
    }
}
