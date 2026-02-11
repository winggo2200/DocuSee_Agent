package com.docuseeagent.service;

import com.docuseeagent.config.Constants;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileCtrlService {
    private final RedisService m_redisService;

    @Async("filectrlExecutor")
    public void FileCtrl(){
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/";
        DeleteDirectory(strFilePath, Constants.REDIS_KEY_UPLOAD);
        DeleteDirectory(strFilePath);

        strFilePath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/";
        DeleteDirectory(strFilePath, Constants.REDIS_KEY_COMPLETED);
        DeleteDirectory(strFilePath);
    }

    private void DeleteDirectory(String _strPath, String _strKey) {
        ObjectMapper mapper = new ObjectMapper();

        Date dateNow = new Date();

        List<String> lstUuids = m_redisService.GetAllListValues(_strKey);

        for(String uuid : lstUuids) {
            try {
                String strInfo = m_redisService.GetValue(uuid);

                if (strInfo == null) {
                    m_redisService.DeleteValue(uuid);
                    m_redisService.RemoveListValue(_strKey, uuid);

                    File fileDocSrc = new File(_strPath + uuid);

                    if (fileDocSrc.exists()) FileUtils.deleteDirectory(fileDocSrc);
                    continue;
                }

                RedisDataInfo info = mapper.readValue(strInfo, RedisDataInfo.class);

                //if(info.status.equals(Constants.REDIS_STATUS_WAIT) || info.status.equals(Constants.REDIS_STATUS_PROC)) continue;

                Date date = info.GetDateTime();

                // 시간 단위로 변경
                long diffHour = (date.getTime() - dateNow.getTime()) / 3600000;
                //long diffHour = (dateNow.getTime() - date.getTime()) / 1000;

                if (diffHour > Constants.TIMEOUT_HOUR_DOC) {
                    m_redisService.DeleteValue(uuid);
                    m_redisService.RemoveListValue(_strKey, uuid);

                    File fileDocSrc = new File(_strPath + uuid);

                    if (fileDocSrc.exists()) FileUtils.deleteDirectory(fileDocSrc);

                    log.info("Delete uuid file : " + uuid);

                }
            } catch (IOException e) {
                log.error(e.getMessage());
                throw new RuntimeException(e);
            }
        }

    }

    public void DeleteDirectory(String _strPath) {
        /// 문서 디렉토리
        Instant now = Instant.now();

        File fileData = new File(_strPath);

        if(!fileData.exists()) return;

        String strFilePath = new File(_strPath).getAbsolutePath() + "/" ;
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get(strFilePath));

            for (Path sub : stream) {
                if (!Files.isDirectory(sub)) continue;

                String strUuid = sub.getFileName().toString();

                String strInfo = m_redisService.GetValue(strUuid);

                if (strInfo != null) {
                    ObjectMapper mapper = new ObjectMapper();
                    RedisDataInfo info = mapper.readValue(strInfo, RedisDataInfo.class);

                    if(info.status.equals(Constants.REDIS_STATUS_WAIT) || info.status.equals(Constants.REDIS_STATUS_PROC)) continue;
                }

                BasicFileAttributes attrs = Files.readAttributes(sub, BasicFileAttributes.class);
                Instant lastTime = attrs.lastModifiedTime().toInstant();

                if (Duration.between(lastTime, now).compareTo(Duration.ofHours(Constants.TIMEOUT_HOUR_DOC)) >= 0) {
                    m_redisService.DeleteValue(strUuid);
                    m_redisService.RemoveListValue(Constants.REDIS_KEY_UPLOAD, strUuid);
                    m_redisService.RemoveListValue(Constants.REDIS_KEY_COMPLETED, strUuid);

                    if (sub.toFile().exists()) FileUtils.deleteDirectory(sub.toFile());
                }
            }
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
