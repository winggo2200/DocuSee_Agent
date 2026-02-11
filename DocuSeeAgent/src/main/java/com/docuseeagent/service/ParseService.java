package com.docuseeagent.service;

import com.docuseeagent.config.Constants;
import com.docuseeagent.docusee.DocuSee;
import com.docuseeagent.dparser.DParser;
import com.docuseeagent.model.parser.ParserRes;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class ParseService {
    private final RedisService m_redisService;

    @Async("parseExecutor")
    public void ExecuteParse(String _strUuid){
        ObjectMapper objectMapper = new ObjectMapper();

        String strData = m_redisService.GetValue(_strUuid);

        if (strData != null) {
            if (!strData.isEmpty()) {
                try {
                    RedisDataInfo dataInfo = objectMapper.readValue(strData, RedisDataInfo.class);

                    dataInfo.status = Constants.REDIS_STATUS_PROC;
                    m_redisService.SetValue(_strUuid, objectMapper.writeValueAsString(dataInfo));
                    m_redisService.RightPushValue(Constants.REDIS_KEY_PROC, _strUuid);

                    Thread.sleep(1000);
                    // parse using cpu
                    String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/CPU";
                    File[] fileList = new File(strFilePath).listFiles(File::isFile);

                    if (fileList != null) {
                        if (fileList.length > 0) {
                            ParserRes structDparserRes = DParser.Upload(_strUuid);

                            if (!structDparserRes.status.equals("success")) {
                                m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                                m_redisService.DeleteValue(_strUuid);
                                m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, _strUuid);
                            }

                            structDparserRes = DParser.Parse(_strUuid);

                            if (!structDparserRes.status.equals("success")) {
                                m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                                m_redisService.DeleteValue(_strUuid);
                                m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, _strUuid);
                            }

                            while(true) {
                                Thread.sleep(1000);
                                structDparserRes = DParser.GetData(_strUuid);

                                if (!structDparserRes.message.equals("Waiting state") && !structDparserRes.message.equals("Processing state") && !structDparserRes.message.equals("Uploading state")) {
                                    break;
                                }
                            }

                            if (!structDparserRes.status.equals("success")) {
                                m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                                m_redisService.DeleteValue(_strUuid);
                                m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, _strUuid);

                                return;
                            }


                            log.info(objectMapper.writeValueAsString(structDparserRes) );
                        }
                    }

                    strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/GPU";
                    fileList = new File(strFilePath).listFiles(File::isFile);

                    if (fileList != null) {
                        if (fileList.length > 0) {
                            ParserRes structDocuseeRes = DocuSee.Parse(_strUuid, m_redisService);

                            if (!structDocuseeRes.status.equals("success")) {
                                m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                                m_redisService.DeleteValue(_strUuid);
                                m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, _strUuid);

                                return;
                            }

                            log.info(objectMapper.writeValueAsString(structDocuseeRes) );

//                                if (structDocuseeRes == null) {
//                                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, strUuid);
//                                    m_redisService.DeleteValue(strUuid);
//                                    m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);
//                                }else{
//                                    JsonNode nodeResult = mapper.readTree(strRes);
//
//
//                                }
                        }
                    }

                    String strDataInfo = m_redisService.GetValue(_strUuid);
                    Thread.sleep(1000);
                    RedisDataInfo redisData = objectMapper.readValue(strDataInfo, RedisDataInfo.class);

                    redisData.status = Constants.REDIS_STATUS_COMPLETED;

                    DateTimeFormatter formatterCompleted = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                    redisData.date = LocalDateTime.now().format(formatterCompleted);

                    m_redisService.SetValue(_strUuid, objectMapper.writeValueAsString(redisData));

                    m_redisService.RightPushValue(Constants.REDIS_KEY_COMPLETED, _strUuid);
                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);



                } catch (Exception e) {
                    m_redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                    m_redisService.DeleteValue(_strUuid);
                    m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, _strUuid);

                    log.error(e.getMessage());
                    throw new RuntimeException(e);

                } finally {

                }
            }
        }else{

        }
    }
}
