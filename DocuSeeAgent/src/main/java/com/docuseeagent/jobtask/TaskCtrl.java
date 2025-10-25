package com.docuseeagent.jobtask;

import com.docuseeagent.service.RedisService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;

@Slf4j
public class TaskCtrl {
    private RedisService m_redisService;

    public TaskCtrl(RedisService redisService) {
        m_redisService = redisService;

        FileCtrlTask fileCtrlTask = new FileCtrlTask(m_redisService);

        ParseTask parserTask1 = new ParseTask(m_redisService);
        ParseTask parserTask2 = new ParseTask(m_redisService);
        ParseTask parserTask3 = new ParseTask(m_redisService);
        ParseTask parserTask4 = new ParseTask(m_redisService);
        ParseTask parserTask5 = new ParseTask(m_redisService);

        SchedulerCtrl schedulerCtrl = new SchedulerCtrl();
        schedulerCtrl.RegisterScheduleWithFixedDelay(fileCtrlTask, Duration.ofHours(1), "FileCtrlTask");

        schedulerCtrl.RegisterScheduleWithFixedDelay(parserTask1, Duration.ofSeconds(1), "ParseTask1");
        schedulerCtrl.RegisterScheduleWithFixedDelay(parserTask2, Duration.ofSeconds(1), "ParseTask2");
        schedulerCtrl.RegisterScheduleWithFixedDelay(parserTask3, Duration.ofSeconds(1), "ParseTask3");
        schedulerCtrl.RegisterScheduleWithFixedDelay(parserTask4, Duration.ofSeconds(1), "ParseTask4");
        schedulerCtrl.RegisterScheduleWithFixedDelay(parserTask5, Duration.ofSeconds(1), "ParseTask5");
    }
}
