package com.docuseeagent.jobtask;


import com.docuseeagent.config.Constants;
import com.docuseeagent.docusee.DParser;
import com.docuseeagent.docusee.DocuSee;
import com.docuseeagent.model.dparser.DparserRes;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
public class ParseTask implements Runnable {
    private RedisService m_redisService;

    public ParseTask(RedisService redisService) {
        m_redisService = redisService;
    }

    @Override
    public void run() {
        //System.out.println("thread id : " + m_nParserId + " - Scheduler 실행");
        String strUuid = m_redisService.LeftPopValue(Constants.REDIS_KEY_WAIT, String.class);

        if (strUuid != null) {
            //System.out.println("thread id : " + m_nParserId + " - Scheduler 처리");
            ObjectMapper mapper = new ObjectMapper();

            String strData = m_redisService.GetValue(strUuid);

            if (strData != null) {
                if (!strData.isEmpty()) {
                    try {
                        RedisDataInfo dataInfo = mapper.readValue(strData, RedisDataInfo.class);

                        dataInfo.status = Constants.REDIS_STATUS_PROC;
                        m_redisService.SetValue(strUuid, mapper.writeValueAsString(dataInfo));
                        m_redisService.RightPushValue(Constants.REDIS_KEY_PROC, strUuid);

                        Thread.sleep(1000);
                        // parse using cpu
                        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + strUuid + "/CPU";
                        File[] fileList = new File(strFilePath).listFiles(File::isFile);

                        if (fileList != null) {
                            if (fileList.length > 0) {
                                DparserRes structDparserRes = DParser.Upload(strUuid);

                                if (!structDparserRes.result.equals("success")) {
                                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);
                                    m_redisService.DeleteValue(strUuid);
                                    m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);
                                }

                                structDparserRes = DParser.Parse(strUuid);

                                if (!structDparserRes.result.equals("success")) {
                                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);
                                    m_redisService.DeleteValue(strUuid);
                                    m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);
                                }

                                while(true) {
                                    Thread.sleep(1000);
                                    structDparserRes = DParser.GetData(strUuid);

                                    if (!structDparserRes.message.equals("Waiting state") && !structDparserRes.message.equals("Processing state") && !structDparserRes.message.equals("Uploading state")) {
                                        break;
                                    }
                                }

                                if (!structDparserRes.result.equals("success")) {
                                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);
                                    m_redisService.DeleteValue(strUuid);
                                    m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);
                                }
                            }
                        }

                        strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + strUuid + "/GPU";
                        fileList = new File(strFilePath).listFiles(File::isFile);

                        if (fileList != null) {
                            if (fileList.length > 0) {
                                String strRes = DocuSee.Parse(strUuid, m_redisService);

                                if (strRes == null) {
                                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);
                                    m_redisService.DeleteValue(strUuid);
                                    m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);
                                }
                            }
                        }

                        String strDataInfo = m_redisService.GetValue(strUuid);
                        ObjectMapper objectMapper = new ObjectMapper();
                        Thread.sleep(1000);
                        RedisDataInfo redisData = objectMapper.readValue(strDataInfo, RedisDataInfo.class);

                        redisData.status = Constants.REDIS_STATUS_COMPLETED;

                        DateTimeFormatter formatterCompleted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                        redisData.date = LocalDateTime.now().format(formatterCompleted);

                        m_redisService.SetValue(strUuid, objectMapper.writeValueAsString(redisData));

                        m_redisService.RightPushValue(Constants.REDIS_KEY_COMPLETED, strUuid);
                        m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);

                    } catch (Exception e) {
                        m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);
                        m_redisService.DeleteValue(strUuid);
                        m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);

                        throw new RuntimeException(e);

                    } finally {

                    }
                }
            }
        }
    }
}
