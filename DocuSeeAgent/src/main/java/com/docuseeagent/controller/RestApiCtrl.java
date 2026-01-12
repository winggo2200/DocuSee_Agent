package com.docuseeagent.controller;

import ch.qos.logback.core.util.FileUtil;
import com.docuseeagent.config.Constants;
import com.docuseeagent.dparser.DParser;
import com.docuseeagent.docusee.DocuSee;
import com.docuseeagent.jobtask.TaskCtrl;
import com.docuseeagent.model.parser.ParserRes;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
public class RestApiCtrl {
    private final RedisService m_redisService;
    private final ObjectMapper objectMapper;

    private TaskCtrl m_taskCtrl = null;

    // init after application is ready
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        //m_redisService.DeleteList(Constants.REDIS_KEY_UPLOAD);
        //m_redisService.DeleteList(Constants.REDIS_KEY_WAIT);
        //m_redisService.DeleteList(Constants.REDIS_KEY_PROC);
        //m_redisService.DeleteList(Constants.REDIS_KEY_COMPLETED);

        log.info("init");
        m_redisService.FlushAll();

//        String strProcUuid = m_redisService.RightPopValue(Constants.REDIS_KEY_PROC, String.class);
//
//        ObjectMapper mapper = new ObjectMapper();
//
//        while (strProcUuid != null && !strProcUuid.isEmpty()) {
//            try {
//                String strDataInfo = m_redisService.GetValue(strProcUuid);
//                RedisDataInfo redisDataInfo = mapper.convertValue(strDataInfo, RedisDataInfo.class);
//
//                redisDataInfo.status = Constants.REDIS_STATUS_WAIT;
//                redisDataInfo.date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
//
//                m_redisService.SetValue(strProcUuid, mapper.writeValueAsString(redisDataInfo));
//
//                m_redisService.LeftPushValue(Constants.REDIS_KEY_WAIT, strProcUuid);
//
//                File fileDocSrc = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + strProcUuid);
//
//                if (fileDocSrc.exists())
//                    FileUtils.deleteDirectory(fileDocSrc);
//
//                strProcUuid = m_redisService.RightPopValue(Constants.REDIS_KEY_PROC, String.class);
//            } catch (Exception e) {
//                ParserRes structParserRes = new ParserRes();
//
//                structParserRes.status = "failure";
//                structParserRes.id = "";
//                structParserRes.message = "Initailization error.";
//
//                try {
//                    log.info(objectMapper.writeValueAsString(structParserRes) );
//                } catch (JsonProcessingException ex) {
//                    log.error(ex.getMessage());
//                    throw new RuntimeException(ex);
//                }
//                return;
//            }
//        }

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

        ParserRes structParserRes = new ParserRes();

        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + strUuid + "/";

        if (_strUuid != null) {
            if (m_redisService.HasValue(Constants.REDIS_KEY_PROC, _strUuid)) {
                structParserRes.status = "failure";
                structParserRes.id = strUuid;
                structParserRes.message = "The document is being processed.";

                try {
                    log.info(objectMapper.writeValueAsString(structParserRes));
                    return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage());
                    throw new RuntimeException(e);
                }
            } else if (m_redisService.HasValue(Constants.REDIS_KEY_COMPLETED, _strUuid)) {
                structParserRes.status = "failure";
                structParserRes.id = strUuid;
                structParserRes.message = "The document parsing is completed.";

                try {
                    log.info(objectMapper.writeValueAsString(structParserRes));
                    return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
                } catch (JsonProcessingException e) {
                    log.error(e.getMessage());
                    throw new RuntimeException(e);
                }
            }

            File fileDocDir = new File(strFilePath);
            try {
                if (fileDocDir.exists()) {
                    if (!bAdd) FileUtils.cleanDirectory(fileDocDir);
                }
            } catch (Exception e) {
                structParserRes.status = "failure";
                structParserRes.id = strUuid;
                structParserRes.message = "Fail to delete directory.";

                try {
                    log.info(objectMapper.writeValueAsString(structParserRes));
                    return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
                } catch (JsonProcessingException ex) {
                    log.error(ex.getMessage());
                    throw new RuntimeException(ex);
                }
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

            File fileTemp = new File(strFilePath + "Temp");

            if (!fileTemp.exists()) {
                FileUtils.forceMkdir(fileTemp);
            }

            List<String> lstNoSupportFileList = new ArrayList<>();
            //List<String> lstNoSupportExcelFileList = new ArrayList<>();
            List<String> lstLimitCells = new ArrayList<>();

            for (MultipartFile file : _files) {
                String strFileName = file.getOriginalFilename();

                String strFileExt = strFileName.substring(strFileName.lastIndexOf(".") + 1);

                strFileExt = strFileExt.toLowerCase();

                if (strFileExt.equals("ppt") || strFileExt.equals("pptx") ||
                        strFileExt.equals("pdf") || strFileExt.equals("jpg") || strFileExt.equals("jpeg")
                        || strFileExt.equals("tiff") || strFileExt.equals("png") || strFileExt.equals("tif")) {
                    File fileDoc = new File(fileGPU.getAbsolutePath() + "/" + file.getOriginalFilename());
                    file.transferTo(fileDoc);

                } else if (strFileExt.equals("doc") || strFileExt.equals("docx")
                        || strFileExt.equals("hwp") || strFileExt.equals("hwpx") || strFileExt.equals("csv")) {
                    File fileDoc = new File(fileCPU.getAbsolutePath() + "/" + file.getOriginalFilename());
                    file.transferTo(fileDoc);
                } else if (strFileExt.equals("xls") || strFileExt.equals("xlsx")) {
                    File fileExcel = new File(fileTemp.getAbsoluteFile() + "/" + file.getOriginalFilename());
                    file.transferTo(fileExcel);

                    FileInputStream fileIS = new FileInputStream(fileExcel);

                    Workbook workbook = null;

                    if (file.getOriginalFilename().endsWith(".xls")) {
                        workbook = new HSSFWorkbook(fileIS);
                    } else if (file.getOriginalFilename().endsWith(".xlsx")) {
                        workbook = new XSSFWorkbook(fileIS);
                    }

                    if (workbook != null) {
                        Boolean bCellSize = CalCellSize(workbook);

                        if (bCellSize) {
                            File fileDoc = new File(fileCPU.getAbsolutePath() + "/" + file.getOriginalFilename());
                            Files.copy(fileExcel.toPath(), fileDoc.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } else {
                            lstLimitCells.add(file.getOriginalFilename());
                        }
                    } else {
                        lstNoSupportFileList.add(file.getOriginalFilename());
                    }
                } else {
                    lstNoSupportFileList.add(file.getOriginalFilename());
                }
            }

            StringBuilder sbMsg = new StringBuilder();

            if (!lstNoSupportFileList.isEmpty()) {
                sbMsg.append("Not supported files: " + String.join(", ", lstNoSupportFileList));
            }

            if (!lstLimitCells.isEmpty()) {
                if (!sbMsg.isEmpty()) sbMsg.append("\n");

                sbMsg.append("Over maximum cells(Excel) : " + String.join(", ", lstLimitCells));
            }

            if (fileTemp.exists()) {
                FileUtils.forceDelete(fileTemp);
            }

            if (_files.length == lstNoSupportFileList.size() + lstLimitCells.size() ) {
                structParserRes.status = "failure";
                structParserRes.id = strUuid;
                structParserRes.message = sbMsg.toString();

                m_redisService.RemoveListValue(Constants.REDIS_KEY_UPLOAD, strUuid);

                log.error(objectMapper.writeValueAsString(structParserRes));

                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
            }


            structParserRes.status = "success";
            structParserRes.id = strUuid;
            structParserRes.message = "Upload completed.";

            if(!sbMsg.isEmpty()) structParserRes.message += ("\n" + sbMsg.toString());

            log.info(objectMapper.writeValueAsString(structParserRes));

            RedisDataInfo redisDataInfo = new RedisDataInfo();
            redisDataInfo.status = Constants.REDIS_STATUS_UPLOAD;

            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            redisDataInfo.date = LocalDateTime.now().format(formatter);

            ObjectMapper mapper = new ObjectMapper();

            m_redisService.SetValue(strUuid, mapper.writeValueAsString(redisDataInfo));

            //if (m_redisService.HasValue(Constants.REDIS_KEY_UPLOAD, strUuid) != null)
            m_redisService.RightPushValue(Constants.REDIS_KEY_UPLOAD, strUuid);

            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
        } catch (IOException e) {
            m_redisService.RemoveListValue(Constants.REDIS_KEY_UPLOAD, strUuid);
            //log.error(e.getMessage());
            structParserRes.status = "failure";
            structParserRes.id = strUuid;
            structParserRes.message = "Fail to upload file.";

            try {
                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
            } catch (JsonProcessingException ex) {
                log.error(ex.getMessage());
                throw new RuntimeException(ex);
            }
            //throw new RuntimeException(e);
        }
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/parse")
    private ResponseEntity ParseDocFiles(@RequestHeader HttpHeaders _header,
                                         @RequestParam("uuid") String _strUuid) {
        String strData = m_redisService.GetValue(_strUuid);

        ParserRes structParserRes = new ParserRes();
        structParserRes.id = _strUuid;

        if (strData != null) {
            if (!strData.isEmpty()) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    RedisDataInfo dataInfo = mapper.readValue(strData, RedisDataInfo.class);

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

                            structParserRes.status = "failure";
                            structParserRes.message = "Fail to add waiting queue.";
                            log.warn(objectMapper.writeValueAsString(structParserRes));

                            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
                        }
                    } else { // upload 이외의 상태
                        structParserRes.status = "failure";
                        structParserRes.message = strStatus + " Only use job for upload state.";
                        log.error(objectMapper.writeValueAsString(structParserRes));

                        return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
                    }
                } catch (Exception e) {
                    structParserRes.status = "failure";
                    structParserRes.message = "Parsing unexpected error";
                    try {
                        log.error(objectMapper.writeValueAsString(structParserRes));
                        return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }

            structParserRes.status = "success";
            structParserRes.message = "Add waiting queue";

            try {
                log.warn(objectMapper.writeValueAsString(structParserRes));
                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }

        structParserRes.status = "failure";
        structParserRes.message = "Not found uploaded data.";
        try {
            log.warn(objectMapper.writeValueAsString(structParserRes));
            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/file/parse")
    public ResponseEntity FileParse(@RequestParam("file") MultipartFile _file) {
        String strUuid = UUID.randomUUID().toString();

        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + strUuid + "/";

        File fileDocDir = new File(strFilePath);
        try {
            if (fileDocDir.exists()) {
                FileUtils.cleanDirectory(fileDocDir);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
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

            File fileTemp = new File(strFilePath + "Temp");

            if (!fileTemp.exists()) {
                FileUtils.forceMkdir(fileTemp);
            }

            String strFileName = _file.getOriginalFilename();

            String strFileExt = strFileName.substring(strFileName.lastIndexOf(".") + 1);

            strFileExt = strFileExt.toLowerCase();

            if (strFileExt.equals("ppt") || strFileExt.equals("pptx") || strFileExt.equals("pdf") || strFileExt.equals("jpg") || strFileExt.equals("jpeg")
                    || strFileExt.equals("tiff") || strFileExt.equals("png") || strFileExt.equals("tif")) {
                File fileDoc = new File(fileGPU.getAbsolutePath() + "/" + _file.getOriginalFilename());
                _file.transferTo(fileDoc);

                ParserRes structDocuseeRes = DocuSee.Parse(strUuid, null);

                //JsonNode node = objectMapper.readTree(strResult);

                if (structDocuseeRes.status.equals("failure")) {
                    String strLog = "File parse failed. - " + strFileName;

                    structDocuseeRes.message = strLog;
                    log.info(objectMapper.writeValueAsString(structDocuseeRes));

                    return new ResponseEntity(objectMapper.writeValueAsString(structDocuseeRes), HttpStatus.OK);
                }

            } else if (strFileExt.equals("doc") || strFileExt.equals("docx")
                    || strFileExt.equals("xls") || strFileExt.equals("xlsx") || strFileExt.equals("hwp") || strFileExt.equals("hwpx")
                    || strFileExt.equals("csv")) {

                if (strFileExt.equals("xls") || strFileExt.equals("xlsx")) {
                    File fileExcel = new File(fileTemp.getAbsoluteFile() + "/" + _file.getOriginalFilename());
                    _file.transferTo(fileExcel);

                    FileInputStream fileIS = new FileInputStream(fileExcel);

                    Workbook workbook = null;

                    if (_file.getOriginalFilename().endsWith(".xls")) {
                        workbook = new HSSFWorkbook(fileIS);
                    } else if (_file.getOriginalFilename().endsWith(".xlsx")) {
                        workbook = new XSSFWorkbook(fileIS);
                    }

                    ParserRes structParserRes = new ParserRes();

                    if (workbook != null) {
                        Boolean bCellSize = CalCellSize(workbook);
                        if (bCellSize) {
                            File fileDoc = new File(fileCPU.getAbsolutePath() + "/" + _file.getOriginalFilename());
                            Files.copy(fileExcel.toPath(), fileDoc.toPath(), StandardCopyOption.REPLACE_EXISTING);

                            if (fileTemp.exists()) {
                                FileUtils.forceDelete(fileTemp);
                            }
                        }
                        else {
                            if (fileTemp.exists()) {
                                FileUtils.forceDelete(fileTemp);
                            }

                            structParserRes.status = "failure";
                            structParserRes.id = strUuid;
                            structParserRes.message = "Over maximum cells(Excel). - " + _file.getOriginalFilename();

                            m_redisService.RemoveListValue(Constants.REDIS_KEY_UPLOAD, strUuid);

                            log.error(objectMapper.writeValueAsString(structParserRes));

                            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                        }
                    }else{
                        if (fileTemp.exists()) {
                            FileUtils.forceDelete(fileTemp);
                        }

                        structParserRes.status = "failure";
                        structParserRes.id = strUuid;
                        structParserRes.message = "No supported file. - " + _file.getOriginalFilename();

                        log.error(objectMapper.writeValueAsString(structParserRes));

                        return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                    }
                }else {
                    File fileDoc = new File(fileCPU.getAbsolutePath() + "/" + _file.getOriginalFilename());
                    _file.transferTo(fileDoc);
                }
                //DParser.Upload(strUuid);
                ParserRes structDparserRes = DParser.Upload(strUuid);

                if (!structDparserRes.status.equals("success")) {

                    String strLog = "File upload failed. - " + strFileName;

                    structDparserRes.message = strLog;
                    log.warn(objectMapper.writeValueAsString(structDparserRes));

                    return new ResponseEntity(objectMapper.writeValueAsString(structDparserRes), HttpStatus.OK);
                }
                structDparserRes = DParser.Parse(strUuid);

                if (!structDparserRes.status.equals("success")) {

                    String strLog = "File parse failed. - " + strFileName;

                    structDparserRes.message = strLog;
                    log.warn(objectMapper.writeValueAsString(structDparserRes));

                    return new ResponseEntity(objectMapper.writeValueAsString(structDparserRes), HttpStatus.OK);
                }

                while (true) {
                    Thread.sleep(1000);
                    structDparserRes = DParser.GetData(strUuid);

                    if (!structDparserRes.message.equals("Waiting state") && !structDparserRes.message.equals("Processing state") && !structDparserRes.message.equals("Uploading state")) {
                        if (structDparserRes.status.equals("failure")) {
                            return new ResponseEntity(objectMapper.writeValueAsString(structDparserRes), HttpStatus.OK);
                        }
                        break;
                    }
                }

            } else {
                ParserRes structParserRes = new ParserRes();
                structParserRes.status = "failure";
                structParserRes.id = strUuid;
                structParserRes.message = "Not supported file. - " + strFileName;

                log.warn(objectMapper.writeValueAsString(structParserRes));

                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
            }


            File fileDir = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + strUuid + "/");

            // 이전에 획득한 적이 있는 경우 기존에 파일이 있는지 확인하여 처리

            File[] fileDirs = fileDir.listFiles(File::isDirectory);

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
                dictResult.put("uuid", strUuid);
                dictResult.put("docs", lstDocDatas);

                String strRes = objMapper.writeValueAsString(dictResult);

                ParserRes structParserRes = new ParserRes();
                structParserRes.status = "success";
                structParserRes.id = strUuid;
                structParserRes.message = strRes;

                log.info("File parse completed. - " + strUuid);

                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);
            } else {

                ParserRes structParserRes = new ParserRes();
                structParserRes.status = "success";
                structParserRes.id = strUuid;
                structParserRes.message = "File parse failed. - " + strUuid;

                log.error(objectMapper.writeValueAsString(structParserRes));
                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }


    @CrossOrigin(origins = "*")
    @PostMapping("/getdata")
    public ResponseEntity GetDocData(@RequestParam("uuid") String _strUuid) {
        String strData = m_redisService.GetValue(_strUuid);

        ParserRes structParserRes = new ParserRes();
        structParserRes.id = _strUuid;

        if (strData != null) {
            if (!strData.isEmpty()) {
                try {
                    ObjectMapper objMapper = new ObjectMapper();

                    RedisDataInfo info = objMapper.readValue(strData, RedisDataInfo.class);

                    switch (info.status) {
                        case Constants.REDIS_STATUS_UPLOAD -> {
                            structParserRes.status = "failure";
                            structParserRes.message = "Uploading state.";

                            log.error(objectMapper.writeValueAsString(structParserRes));
                            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                        }
                        case Constants.REDIS_STATUS_WAIT -> {
                            structParserRes.status = "failure";
                            structParserRes.message = "Waiting state.";

                            log.error(objectMapper.writeValueAsString(structParserRes));
                            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                        }
                        case Constants.REDIS_STATUS_PROC -> {
                            structParserRes.status = "failure";
                            structParserRes.message = "Processing state.";

                            log.error(objectMapper.writeValueAsString(structParserRes));
                            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                        }
                        case Constants.REDIS_STATUS_COMPLETED -> {
                            File fileDir = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/");

                            // 이전에 획득한 적이 있는 경우 기존에 파일이 있는지 확인하여 처리

                            if (!fileDir.exists()) {
                                structParserRes.status = "failure";
                                structParserRes.message = "Not found parsed file.";

                                log.error(objectMapper.writeValueAsString(structParserRes));
                                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                            }

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

                                structParserRes.status = "success";
                                structParserRes.message = strRes;

                                log.info(objectMapper.writeValueAsString(structParserRes));
                                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.OK);

                            } else {
                                structParserRes.status = "failure";
                                structParserRes.message = "Fail to parse file.";

                                log.error(objectMapper.writeValueAsString(structParserRes));
                                return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error(e.getMessage());
                    return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
                }
            }
        }
        structParserRes.status = "failure";
        structParserRes.message = "Not found parsed data.";

        try {
            log.error(objectMapper.writeValueAsString(structParserRes));
            return new ResponseEntity(objectMapper.writeValueAsString(structParserRes), HttpStatus.BAD_REQUEST);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }


    @CrossOrigin(origins = "*")
    @GetMapping("/img/get/resource")
    private ResponseEntity<Resource> LoadImageResource(@RequestParam("uuid") String _strUUID, @RequestParam("file") String _strFileName, @RequestParam("filename") String _strImgfileName) {
        String strDstPath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/";
        String strImgPath = strDstPath + _strUUID + "/";

        if (_strFileName.contains(".")) {
            strImgPath += _strFileName.substring(0, _strFileName.lastIndexOf(".")) + "_" + _strFileName.substring(_strFileName.lastIndexOf(".") + 1);
        } else {
            String strLog = "Not found " + _strFileName;

            log.error(strLog);
            return new ResponseEntity(strLog, HttpStatus.BAD_REQUEST);
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

            log.error(strLog);

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
            log.error(e.getMessage());
            return new ResponseEntity<Resource>(HttpStatus.BAD_REQUEST);
        }

        log.info("Load file completed. - " + _strSrcFile);
        return new ResponseEntity<Resource>(resourceFile, header, HttpStatus.OK);
    }

    private Boolean CalCellSize(Workbook _workbook) {
        for (int i = 0; i < _workbook.getNumberOfSheets(); ++i) {
            Sheet sheet = _workbook.getSheetAt(i);

            long nCells = 0;

            for (int nRowIdx = 0; nRowIdx < sheet.getLastRowNum(); ++nRowIdx) {
                Row row = sheet.getRow(nRowIdx);

                if (row != null) {
                    nCells += row.getLastCellNum() - row.getFirstCellNum();
                }
            }

            if (nCells > Constants.LIMIT_CELLS) return false;
        }

        return true;
    }


}
