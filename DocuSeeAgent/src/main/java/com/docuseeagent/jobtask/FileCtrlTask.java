package com.docuseeagent.jobtask;


import com.docuseeagent.config.Constants;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.util.http.fileupload.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.List;

public class FileCtrlTask implements Runnable {
    private RedisService m_redisService;

    public FileCtrlTask(RedisService _redisService) {
        m_redisService = _redisService;
    }

    public void run() {
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/";
        DeleteDirectory(strFilePath, Constants.REDIS_KEY_UPLOAD);

        strFilePath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/";
        DeleteDirectory(strFilePath, Constants.REDIS_KEY_COMPLETED);
    }

    private void DeleteDirectory(String _strPath, String _strKey) {
        ObjectMapper mapper = new ObjectMapper();

        Date dateNow = new Date();

        List<String> lstUuids = m_redisService.GetAllListValues(_strKey);

        lstUuids.forEach(uuid -> {
            try {
                String strInfo = m_redisService.GetValue(uuid);

                RedisDataInfo info = mapper.readValue(strInfo, RedisDataInfo.class);
                Date date = info.GetDateTime();

                // 시간 단위로 변경
                long diffHour = (date.getTime() - dateNow.getTime()) / 3600000;
                //long diffHour = (dateNow.getTime() - date.getTime()) / 1000;

                if (diffHour > Constants.TIMEOUT_HOUR_DOC) {
                    m_redisService.DeleteValue(uuid);
                    m_redisService.RemoveListValue(_strKey, uuid);

                    File fileDocSrc = new File(_strPath + uuid);

                    if (fileDocSrc.exists()) FileUtils.deleteDirectory(fileDocSrc);

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

    }
}
