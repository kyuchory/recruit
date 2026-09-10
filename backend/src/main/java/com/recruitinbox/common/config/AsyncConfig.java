package com.recruitinbox.common.config;


import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Bounded pools so slow analysis never starves notification dispatch
 * (v1.1 section 5.2). Queues are finite; overflow runs on the caller
 * (the poll loop), which naturally throttles claiming.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncConfig {

    @Bean(name = "parserExecutor")
    public ThreadPoolTaskExecutor parserExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("parser-");
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(2);
        ex.setQueueCapacity(50);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

    @Bean(name = "notificationExecutor")
    public org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setThreadNamePrefix("notify-");
        ex.setCorePoolSize(4);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(100);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }
}
