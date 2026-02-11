package com.docuseeagent.service;

import com.docuseeagent.config.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class ScheduleService {
    private final Executor m_ParseExecuter;

    private final Executor m_FilectrlExecuter;

    private final RedisService m_redisService;

    private final FileCtrlService m_FileCtrlService;

    private final ParseService m_ParseService;

    public ScheduleService(@Qualifier("parseExecutor") Executor _parseExecutor, @Qualifier("filectrlExecutor") Executor _filectrlExecutor,
                           RedisService _redisService, FileCtrlService _fileCtrlService, ParseService _parseService) {
        m_ParseExecuter = _parseExecutor;
        m_FilectrlExecuter = _filectrlExecutor;
        m_redisService = _redisService;
        m_FileCtrlService = _fileCtrlService;
        m_ParseService = _parseService;
    }

    // 1초마다 확인
    @Scheduled(fixedRate = 1000)
    public void ConsumeParseQueue() {
        if (m_ParseExecuter instanceof ThreadPoolTaskExecutor parseExecutor) {
            // 실행중인 thread 수 획득
            int nProcCount = parseExecutor.getActiveCount();

            // 여유 thread 계산
            int nAvailableSlots = Constants.MAX_CONCURRENT_JOBS - nProcCount;

            // 여유 thread가 있을 경우 수행
            if (nAvailableSlots > 0) {
                // 여유 공간만큼 반복해서 큐에서 작업을 꺼냄
                for (int i = 0; i < nAvailableSlots; i++) {
                    // Redis에 대기 중인 작업의 uuid 추출
                    String strUuid = m_redisService.LeftPopValue(Constants.REDIS_KEY_WAIT, String.class);

                    if (strUuid == null) continue;

                    m_ParseService.ExecuteParse(strUuid);
                }
            }
        }
    }

    // 1 시간 마다 확인
    @Scheduled(fixedRate = 3600000)
    public void ConsumeFileCtrlQueue(){
        if(m_FilectrlExecuter instanceof ThreadPoolTaskExecutor filectrlExecuter){
            // 실행중인 thread 수
            int nProcCount = filectrlExecuter.getActiveCount();

            // 실행중인 thread가 없을 경우 수행
            if(nProcCount == 0) {
                m_FileCtrlService.FileCtrl();
            }
        }
    }


}
