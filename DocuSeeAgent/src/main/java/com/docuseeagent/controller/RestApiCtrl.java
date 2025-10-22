package com.docuseeagent.controller;

import com.docuseeagent.config.Constants;
import com.docuseeagent.docusee.DParser;
import com.docuseeagent.docusee.DocuSee;
import com.docuseeagent.jobtask.TaskCtrl;
import com.docuseeagent.model.dparser.DparserRes;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class RestApiCtrl {
    private final RedisService m_redisService;
    private final ObjectMapper objectMapper;

    private TaskCtrl m_taskCtrl = null;

    // init after application is ready
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        m_redisService.DeleteList(Constants.REDIS_KEY_UPLOAD);
        m_redisService.DeleteList(Constants.REDIS_KEY_WAIT);
        m_redisService.DeleteList(Constants.REDIS_KEY_PROC);
        m_redisService.DeleteList(Constants.REDIS_KEY_COMPLETED);

        // Task(Thread) Controller 초기화
        if (m_taskCtrl == null) {
            m_taskCtrl = new TaskCtrl(m_redisService);
        }
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/upload")
    private ResponseEntity UploadDocFiles(@RequestHeader HttpHeaders _header, @RequestParam(value = "uuid", required = false) String _strUuid,
                                          @RequestParam("files") MultipartFile[] _files, @RequestParam(value = "add", required = false) Boolean _bAdd) {
        String strUuid = _strUuid;

        Boolean bAdd = false;

        if (strUuid == null || strUuid.isEmpty())
            strUuid = UUID.randomUUID().toString();
        else {
            if (_bAdd != null) bAdd = _bAdd;
        }

        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + strUuid + "/";

        if (_strUuid != null) {
            if (m_redisService.HasValue(Constants.REDIS_KEY_PROC, _strUuid))
                return new ResponseEntity("The document is being processed.", HttpStatus.BAD_REQUEST);
            else if (m_redisService.HasValue(Constants.REDIS_KEY_COMPLETED, _strUuid))
                return new ResponseEntity("The document has been processed.", HttpStatus.BAD_REQUEST);

            File fileDocDir = new File(strFilePath);
            try {
                if (fileDocDir.exists()) {
                    if (!bAdd) FileUtils.cleanDirectory(fileDocDir);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        try {
            File fileCPU = new File(strFilePath + "CPU");
            if (!fileCPU.exists()) {
                FileUtils.forceMkdir(fileCPU);
            }

            File fileGPU = new File(strFilePath + "GPU");

            if (!fileGPU.exists()) {
                FileUtils.forceMkdir(fileGPU);
            }

            for (MultipartFile file : _files) {
                String strFileName = file.getOriginalFilename();

                String strFileExt = strFileName.substring(strFileName.lastIndexOf(".") + 1);

                strFileExt = strFileExt.toLowerCase();

                if (strFileExt.equals("pdf") || strFileExt.equals("jpg") || strFileExt.equals("jpeg")
                        || strFileExt.equals("tiff") || strFileExt.equals("png") || strFileExt.equals("tif")) {
                    File fileDoc = new File(fileGPU.getAbsolutePath() + "/" + file.getOriginalFilename());
                    file.transferTo(fileDoc);

                } else if (strFileExt.equals("ppt") || strFileExt.equals("pptx") || strFileExt.equals("doc") || strFileExt.equals("docx")
                        || strFileExt.equals("xls") || strFileExt.equals("xlsx") || strFileExt.equals("hwp") || strFileExt.equals("hwpx")
                        || strFileExt.equals("csv")) {
                    File fileDoc = new File(fileCPU.getAbsolutePath() + "/" + file.getOriginalFilename());
                    file.transferTo(fileDoc);
                }
            }

            RedisDataInfo redisDataInfo = new RedisDataInfo();

            redisDataInfo.status = Constants.REDIS_STATUS_UPLOAD;
            redisDataInfo.dparserId = strUuid;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            redisDataInfo.date = LocalDateTime.now().format(formatter);

            ObjectMapper mapper = new ObjectMapper();

            m_redisService.SetValue(strUuid, mapper.writeValueAsString(redisDataInfo));

            if (m_redisService.HasValue(Constants.REDIS_KEY_UPLOAD, strUuid) != null)
                m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);

            return new ResponseEntity(strUuid, HttpStatus.OK);
        } catch (IOException e) {
            m_redisService.RemoveListValue(Constants.REDIS_KEY_UPLOAD, strUuid);
            throw new RuntimeException(e);
        }
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/parse")
    private ResponseEntity ParseDocFiles(@RequestHeader HttpHeaders _header,
                                         @RequestParam("uuid") String _strUuid) {
        String strData = m_redisService.GetValue(_strUuid);
        if (strData != null) {
            if (!strData.isEmpty()) {
                try {

                    ObjectMapper mapper = new ObjectMapper();
                    RedisDataInfo dataInfo = null;

                    dataInfo = mapper.readValue(strData, RedisDataInfo.class);

                    String strStatus = dataInfo.status;

                    if (strStatus.equals(Constants.REDIS_STATUS_UPLOAD)) { // upload 상태
                        m_redisService.RemoveListValue(Constants.REDIS_KEY_UPLOAD, _strUuid);

                        RedisDataInfo redisDataInfo = new RedisDataInfo();

                        redisDataInfo.status = Constants.REDIS_STATUS_WAIT;
                        m_redisService.SetValue(_strUuid, mapper.writeValueAsString(dataInfo));

                        // waiting queue 등록
                        Long lCnt = m_redisService.RightPushValue(Constants.REDIS_KEY_WAIT, _strUuid);
                        if (lCnt <= 0) { // waiting queue 등록 실패
                            dataInfo.status = Constants.REDIS_STATUS_UPLOAD;
                            m_redisService.SetValue(_strUuid, mapper.writeValueAsString(dataInfo));

                            return new ResponseEntity("Fail to add waiting queue", HttpStatus.OK);
                        }
                    } else { // upload 이외의 상태
                        return new ResponseEntity(strStatus, HttpStatus.OK);
                    }
                } catch (Exception e) {
                    return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
                }
            }

            return new ResponseEntity(_strUuid, HttpStatus.OK);
        }

        return new ResponseEntity("No uploaded data found.", HttpStatus.BAD_REQUEST);

    }

    @CrossOrigin(origins = "*")
    @PostMapping("/parsing-sync")
    public ResponseEntity ParsingSync(@RequestParam("uuid") String _strUuid) {
        // parse using cpu
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/CPU";
        File[] fileList = new File(strFilePath).listFiles(File::isFile);

        if (fileList != null) {
            if (fileList.length > 0) {
                DparserRes structDparserRes = DParser.Upload(_strUuid);

                if (!structDparserRes.result.equals("success")) {
                    return new ResponseEntity(structDparserRes.message, HttpStatus.BAD_REQUEST);
                }

                structDparserRes = DParser.Parse(_strUuid);

                if (!structDparserRes.result.equals("success")) {
                    return new ResponseEntity(structDparserRes.message, HttpStatus.BAD_REQUEST);
                }
            }
        }

        // parse using gpu
        strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/GPU";
        fileList = new File(strFilePath).listFiles(File::isFile);

        if (fileList != null) {
            if (fileList.length > 0) {
                String strRes = DocuSee.Parse(_strUuid, m_redisService);

                if (strRes == null) {
                    return new ResponseEntity("Parsing(GPU) Error.\n", HttpStatus.BAD_REQUEST);
                }
            }
        }

        File fileDir = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/");

        // 이전에 획득한 적이 있는 경우 기존에 파일이 있는지 확인하여 처리

        File[] fileDirs = fileDir.listFiles(File::isDirectory);

        try {
            ObjectMapper objMapper = new ObjectMapper();

            if (fileDirs.length > 0) {
                List<Object> lstDocDatas = new ArrayList<>();
                for (File file : fileDirs) {
                    File fileJson = new File(file.getAbsolutePath() + "/DATA/result.json");

                    if (fileJson.exists()) {
                        JsonNode nodeFileData = objMapper.readTree(Files.readString(Paths.get(fileJson.getAbsolutePath())));
                        lstDocDatas.add(nodeFileData);
                    }
                }

                HashMap<String, Object> dictResult = new HashMap<>();
                dictResult.put("uuid", _strUuid);
                dictResult.put("docs", lstDocDatas);

                String strRes = objMapper.writeValueAsString(dictResult);

                return new ResponseEntity(strRes, HttpStatus.OK);
            } else {
                return new ResponseEntity("No processed data found.", HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    @CrossOrigin(origins = "*")
    @PostMapping("/getdata")
    public ResponseEntity GetDocData(@RequestParam("uuid") String _strUuid) {
        String strData = m_redisService.GetValue(_strUuid);

        if(strData != null ) {
            if (!strData.isEmpty()) {
                try {
                    ObjectMapper objMapper = new ObjectMapper();

                    RedisDataInfo info = objMapper.readValue(strData, RedisDataInfo.class);

                    switch (info.status) {
                        case Constants.REDIS_STATUS_UPLOAD -> {
                            return new ResponseEntity("Upload state", HttpStatus.BAD_REQUEST);
                        }
                        case Constants.REDIS_STATUS_WAIT -> {
                            return new ResponseEntity("Waiting state", HttpStatus.BAD_REQUEST);
                        }
                        case Constants.REDIS_STATUS_PROC -> {
                            return new ResponseEntity("Processing state", HttpStatus.BAD_REQUEST);
                        }
                        case Constants.REDIS_STATUS_COMPLETED -> {
                            File fileDir = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/");

                            // 이전에 획득한 적이 있는 경우 기존에 파일이 있는지 확인하여 처리

                            File[] fileDirs = fileDir.listFiles(File::isDirectory);

                            if (fileDirs.length > 0) {
                                List<Object> lstDocDatas = new ArrayList<>();
                                for (File file : fileDirs) {
                                    File fileJson = new File(file.getAbsolutePath() + "/DATA/result.json");

                                    if (fileJson.exists()) {
                                        JsonNode nodeFileData = objMapper.readTree(Files.readString(Paths.get(fileJson.getAbsolutePath())));
                                        lstDocDatas.add(nodeFileData);
                                    }
                                }

                                HashMap<String, Object> dictResult = new HashMap<>();
                                dictResult.put("uuid", _strUuid);
                                dictResult.put("docs", lstDocDatas);

                                String strRes = objMapper.writeValueAsString(dictResult);

                                return new ResponseEntity(strRes, HttpStatus.OK);
                            } else {
                                return new ResponseEntity("No processed data found.", HttpStatus.BAD_REQUEST);
                            }
                        }
                    }
                } catch (Exception e) {
                    return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
                }

            }

        }
        return new ResponseEntity("No processed data found.", HttpStatus.BAD_REQUEST);

//        DparserRes structDparserRes = DParser.GetData(_strUuid);
//
//        if (structDparserRes.result.equals("success")) {
//            //lstUuidProc.remove(_strUuid);
//            return new ResponseEntity(structDparserRes.message, HttpStatus.OK);
//        } else {
//            return new ResponseEntity(structDparserRes.message, HttpStatus.BAD_REQUEST);
//        }
    }


    @CrossOrigin(origins = "*")
    @GetMapping("/img/get/resource")
    private ResponseEntity<Resource> LoadImageResource(@RequestParam("uuid") String _strUUID, @RequestParam("file") String _strFileName, @RequestParam("filename") String _strImgfileName) {
        String strDstPath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/";
        String strImgPath = strDstPath + _strUUID + "/";

        if (_strFileName.contains(".")) {
            strImgPath += _strFileName.substring(0, _strFileName.lastIndexOf(".")) + "_" + _strFileName.substring(_strFileName.lastIndexOf(".") + 1);
        } else {
            String strLog = "Not found. " + _strFileName;

            return new ResponseEntity("Not found.", HttpStatus.BAD_REQUEST);
        }

        strImgPath += "/DATA/" + _strImgfileName;

        return LoadFile(strImgPath);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/img/get/doc")
    private ResponseEntity<Resource> GetDocImage(@RequestParam("uuid") String _strUUID, @RequestParam("file") String _strFileName, @RequestParam("page") String _strPageNum) {
        String strDstPath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/";
        String strImgPath = strDstPath + _strUUID + "/";

        if (_strFileName.contains(".")) {
            strImgPath += _strFileName.substring(0, _strFileName.lastIndexOf(".")) + "_" + _strFileName.substring(_strFileName.lastIndexOf(".") + 1);
        } else {
            String strLog = "Not found doc image. - " + _strFileName + "\n";

            return new ResponseEntity(strLog, HttpStatus.BAD_REQUEST);
        }

        strImgPath += "/JPEG/" + _strPageNum + ".jpg";

        return LoadFile(strImgPath);
    }

    private ResponseEntity<Resource> LoadFile(String _strSrcFile) {
        Resource resourceFile = new FileSystemResource(_strSrcFile);

        if (!resourceFile.exists())
            return new ResponseEntity<Resource>(HttpStatus.BAD_REQUEST);

        HttpHeaders header = new HttpHeaders();
        try {
            //String extension = StringUtils.getFilenameExtension(_strSrcFile);

            if (_strSrcFile.equals("txt"))
                header.add("Content-type", Files.probeContentType(Paths.get(_strSrcFile)) + ";charset=UTF-8");
            else
                header.add("Content-type", Files.probeContentType(Paths.get(_strSrcFile)));
        } catch (Exception e) {
            return new ResponseEntity<Resource>(HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<Resource>(resourceFile, header, HttpStatus.OK);
    }


}
