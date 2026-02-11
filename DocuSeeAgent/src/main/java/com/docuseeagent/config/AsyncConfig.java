package com.docuseeagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {
    @Bean(name = "parseExecutor")
    public Executor ParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // 1. 기본 스레드 수 (동시 처리 5개)
        executor.setCorePoolSize(Constants.MAX_CONCURRENT_JOBS);

        // 2. 최대 스레드 수 (5개로 고정하여 그 이상 동시에 실행되지 않도록 함)
        executor.setMaxPoolSize(Constants.MAX_CONCURRENT_JOBS);

        // 3. 대기열 크기 (5개가 꽉 찼을 때 대기할 수 있는 요청 수)
        // 100개가 꽉 차면 그 이후 요청은 예외 발생(RejectedExecutionException)
        //executor.setQueueCapacity(100);

        executor.setThreadNamePrefix("Async-Job-Parse-");

        // (선택) 큐와 풀이 모두 꽉 찼을 때 처리 정책 (CallerRuns: 요청한 스레드에서 직접 실행)
        //executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        executor.initialize();
        return executor;
    }

    @Bean(name = "filectrlExecutor")
    public Executor FilectrlExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);

        executor.setMaxPoolSize(1);

        executor.setThreadNamePrefix("Async-Job-FileCtrl-");
        executor.initialize();
        return executor;
    }


}
