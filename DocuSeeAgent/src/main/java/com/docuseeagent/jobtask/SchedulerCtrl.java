package com.docuseeagent.jobtask;

import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;

public class SchedulerCtrl {
    private final static int POOL_SIZE = 6;

    private ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
    public Map<String, ScheduledFuture<?>> mapScheduled = new ConcurrentHashMap<>();

    public SchedulerCtrl() {
        scheduler.setPoolSize(POOL_SIZE);
        // thread prefix
        scheduler.setThreadNamePrefix("scheduler-thread-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.initialize();
    }

    // Task 상태 확인
//    public String GetTaskStatus(String _strUuid){
//        ScheduledFuture<?> task = mapScheduled.get(_strUuid);
//
//        if(task == null){
//            return "UNKNOWN";
//        }
//
//        Future.State state = task.state();
//
//        switch (state){
//            case RUNNING:
//                return "RUNNING";
//            case FAILED:
//                return "FAILED";
//            case CANCELLED:
//                return "CANCELLED";
//            case SUCCESS:
//                return "SUCCESS";
//            default:
//                return "UNKNOWN";
//        }
//    }

    @Bean
    public ThreadPoolTaskScheduler taskScheduler(){
        return scheduler;
    }

    // Task 등록
    public void RegisterSchedule(Runnable _run, String _strId){
        Date dateNow = new Date(System.currentTimeMillis() + 1000);
        ScheduledFuture<?> task = scheduler.schedule(_run, dateNow);

        mapScheduled.put(_strId, task);
    }

    // Task 등록 - 일정 시간 마다 동작
    public void RegisterScheduleWithFixedDelay(Runnable _run, Duration _delay, String _strId){
        ScheduledFuture<?> task = scheduler.scheduleWithFixedDelay(_run, _delay);
        mapScheduled.put(_strId, task);
    }

    // Task 제거
    public void RemoveTask(String _strId) {
        mapScheduled.get(_strId).cancel(true);
        mapScheduled.remove(_strId);
    }
}
