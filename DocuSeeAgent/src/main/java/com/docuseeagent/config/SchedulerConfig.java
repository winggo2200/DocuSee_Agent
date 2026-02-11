package com.docuseeagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@EnableScheduling
@Configuration
public class SchedulerConfig {
    @Bean
    public TaskScheduler ScheduleExecuter(){
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();

        scheduler.setPoolSize(6);

        scheduler.setThreadNamePrefix("Schedule-");

        scheduler.initialize();
        return scheduler;
    }
}
