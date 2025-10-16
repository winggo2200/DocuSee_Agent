package com.docuseeagent.controller;

import com.docuseeagent.config.Constants;
import com.docuseeagent.docusee.DParser;
import com.docuseeagent.dparser.structure.StructDparserRes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.util.http.fileupload.FileUtils;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RestController
public class RestApiCtrl {
    // 현재 처리중인 uuid 목록 (CPU, GPU)
    public static List<String> lstUuidProc = new ArrayList<>();

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
            if (lstUuidProc.contains(_strUuid)) {
                String strRes = "The document is being processed.";
                return new ResponseEntity(strRes, HttpStatus.BAD_REQUEST);
            }

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

            return new ResponseEntity(strUuid, HttpStatus.OK);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/parse")
    private ResponseEntity ParseDocFiles(@RequestHeader HttpHeaders _header,
                                         @RequestParam("uuid") String _strUuid) {
        if (lstUuidProc.contains(_strUuid)) {
            String strRes = "The document is being processed.";
            return new ResponseEntity(strRes, HttpStatus.BAD_REQUEST);
        }

        lstUuidProc.add(_strUuid);

        // file upload
        StructDparserRes structDparserRes = DParser.Upload(_strUuid);

        if (!structDparserRes.result.equals("success")) {
            lstUuidProc.remove(_strUuid);
            return new ResponseEntity(structDparserRes.message, HttpStatus.BAD_REQUEST);
        }

        structDparserRes = DParser.Parse(_strUuid);

        if (structDparserRes.result.equals("success")) {
            return new ResponseEntity(structDparserRes.message, HttpStatus.OK);
        } else {
            lstUuidProc.remove(_strUuid);
            return new ResponseEntity(structDparserRes.message, HttpStatus.BAD_REQUEST);
        }
    }


    @CrossOrigin(origins = "*")
    @PostMapping("/getdata")
    public ResponseEntity GetDocData(@RequestParam("uuid") String _strUuid) {
        ObjectMapper objMapper = new ObjectMapper();

        File fileDir = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/");

        // 이전에 획득한 적이 있는 경우 기존에 파일이 있는지 확인하여 처리
        if (!lstUuidProc.contains(_strUuid)) {
            File[] fileDirs = fileDir.listFiles(File::isDirectory);

            List<Object> lstDocDatas = new ArrayList<>();

            try {
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
            } catch (Exception e) {
                return new ResponseEntity(e.getMessage(), HttpStatus.BAD_REQUEST);
            }
        }

        StructDparserRes structDparserRes = DParser.GetData(_strUuid);

        if (structDparserRes.result.equals("success")) {
            lstUuidProc.remove(_strUuid);
            return new ResponseEntity(structDparserRes.message, HttpStatus.OK);
        } else {
            return new ResponseEntity(structDparserRes.message, HttpStatus.BAD_REQUEST);
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
