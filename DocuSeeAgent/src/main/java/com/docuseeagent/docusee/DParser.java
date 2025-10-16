package com.docuseeagent.docusee;

import com.docuseeagent.config.Constants;
import com.docuseeagent.dparser.structure.StructDparserRes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DParser {
    public static StructDparserRes Upload(String _strUuid) {
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/CPU";

        File[] fileList = new File(strFilePath).listFiles(File::isFile);

        String strDparserAddr = Constants.SERVER_ADDR_CPU + "/upload";

        WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build()).build();

        StructDparserRes structDparserResResult = new StructDparserRes();

        structDparserResResult.id = _strUuid;

        if (fileList != null) {
            if (fileList.length > 0) {
                for (File file : fileList) {
                    Resource resourceFile = new FileSystemResource(file);

                    MultipartBodyBuilder builder = new MultipartBodyBuilder();

                    builder.part("uuid", _strUuid);
                    builder.part("srcfile", resourceFile);
                    builder.part("add", true);

                    String strResult = webClient.post().uri(strDparserAddr).body(BodyInserters.fromMultipartData(builder.build())).retrieve().bodyToMono(String.class).block();

                    StructDparserRes structDparserRes;

                    ObjectMapper objMapper = new ObjectMapper();
                    try {
                        structDparserRes = objMapper.readValue(strResult, StructDparserRes.class);
                    } catch (Exception e) {
                        structDparserResResult.result = "failure";

                        structDparserResResult.message = e.getMessage();

                        return structDparserResResult;
                    }

                    if (!structDparserRes.result.equals("success")) {
                        return structDparserRes;
                    }
                }
            } else {
                structDparserResResult.result = "failure";
                structDparserResResult.message = "No files to upload.";

                return structDparserResResult;
            }
        }

        structDparserResResult.result = "success";
        structDparserResResult.message = "Upload success.";
        return structDparserResResult;

    }

    public static StructDparserRes Parse(String _strUuid) {
        String strDparserAddr = Constants.SERVER_ADDR_CPU + "/parse";

        WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build()).build();

        LinkedMultiValueMap mapBody = new LinkedMultiValueMap();
        mapBody.add("uuid", _strUuid);

        String strResult = webClient.post().uri(strDparserAddr).body(BodyInserters.fromFormData(mapBody)).retrieve().bodyToMono(String.class).block();

        StructDparserRes structDparserResResult = new StructDparserRes();


        ObjectMapper objMapper = new ObjectMapper();
        StructDparserRes structDparserRes;
        try {
            structDparserRes = objMapper.readValue(strResult, StructDparserRes.class);
        } catch (Exception e) {
            structDparserResResult.id = _strUuid;
            structDparserResResult.result = "failure";
            structDparserResResult.message = e.getMessage();

            return structDparserResResult;
        }

        return structDparserRes;
    }

    public static StructDparserRes GetData(String _strUuid) {
        ObjectMapper objMapper = new ObjectMapper();

        File fileDir = new File(new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/");

        WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build()).build();

        String strDparserAddr = Constants.SERVER_ADDR_CPU + "/getdata";

        LinkedMultiValueMap mapBody = new LinkedMultiValueMap();
        mapBody.add("uuid", _strUuid);

        StringBuilder sbResult = new StringBuilder();

        String strResult = webClient.post().uri(strDparserAddr).body(BodyInserters.fromFormData(mapBody)).retrieve().bodyToMono(String.class).onErrorResume(
                WebClientResponseException.class, ex -> {
                    if (ex.getStatusCode() == HttpStatus.BAD_REQUEST) sbResult.append(ex.getResponseBodyAsString());
                    return Mono.just(sbResult.toString());
                }
        ).block();

        try {
            StructDparserRes structDparserRes = objMapper.readValue(strResult, StructDparserRes.class);

            if (structDparserRes.result.equals("success")) {
                try {
                    JsonNode nodeData = objMapper.readTree(structDparserRes.message);

                    File[] fileDirs = fileDir.listFiles(File::isDirectory);

                    // 이미 저장되어 있는 파일 목록
                    List<String> lstDocFiles = new ArrayList<>();
                    if (fileDirs != null) {
                        for (File file : fileDirs) {
                            File fileJson = new File(file.getAbsolutePath() + "/DATA/result.json");

                            if (fileJson.exists()) {
                                String strDir = file.getName();
                                String[] astrFileName = strDir.split("_");

                                lstDocFiles.add(astrFileName[0] + "." + astrFileName[1]);
                            }
                        }
                    }

                    for (JsonNode nodeFiles : nodeData) {
                        for (JsonNode fileData : nodeFiles) {
                            String strFileName = fileData.get("filename").asText();

                            boolean bEnable = false;

                            for (String strDocFileName : lstDocFiles) {
                                if (strDocFileName.equals(strFileName)) {
                                    bEnable = true;
                                    break;
                                }
                            }

                            if (bEnable) break;

                            //String[] astrFileName = strFileName.split("\\.");

                            String strFileFirst = strFileName.substring(0, strFileName.lastIndexOf("."));
                            String strFileLast = strFileName.substring(strFileName.lastIndexOf(".") + 1);

                            String strResultPath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/" + strFileFirst + "_" + strFileLast + "/";

                            String strJsonPath = strResultPath + "DATA";
                            String strImgPath = strResultPath + "JPEG";

                            File dirJson = new File(strJsonPath);

                            if (!dirJson.exists())
                                FileUtils.forceMkdir(new File(strJsonPath));

                            File dirImg = new File(strImgPath);

                            if (!dirImg.exists())
                                FileUtils.forceMkdir(new File(strImgPath));

                            JsonNode nodePages = fileData.get("pages");

                            int idx = 1;


                            // resource image
                            mapBody.clear();
                            mapBody.add("uuid", _strUuid);
                            mapBody.add("file", strFileName);

                            strDparserAddr = Constants.SERVER_ADDR_CPU + "/img/list/resource";
                            strResult = webClient.post().uri(strDparserAddr).body(BodyInserters.fromFormData(mapBody)).retrieve().bodyToMono(String.class).block();

                            JsonNode nodeResourceImgs = objMapper.readTree(strResult);

                            if (nodeResourceImgs.get("result").asText().equals("success")) {
                                JsonNode nodeListImg = objMapper.readTree(nodeResourceImgs.get("message").asText());

                                for (JsonNode nodeResoureImg : nodeListImg) {
                                    String strResourceImg = nodeResoureImg.asText();
                                    String strResourceImgAddr = Constants.SERVER_ADDR_CPU + "/img/get/resource";

                                    mapBody.clear();
                                    mapBody.add("uuid", _strUuid);
                                    mapBody.add("file", strFileName);
                                    mapBody.add("filename", strResourceImg);

                                    byte[] byResourceImg;
                                    byResourceImg = webClient.post().uri(strResourceImgAddr).body(BodyInserters.fromFormData(mapBody)).accept(MediaType.MULTIPART_FORM_DATA).retrieve().bodyToMono(byte[].class).block();

                                    if (byResourceImg != null) {
                                        FileOutputStream fosImgData = new FileOutputStream(strJsonPath + "/" + nodeResoureImg.asText());
                                        fosImgData.write(byResourceImg);
                                        fosImgData.close();
                                    }
                                }
                            }

                            //  document image
                            strDparserAddr = Constants.SERVER_ADDR_CPU + "/img/list/doc";
                            strResult = webClient.post().uri(strDparserAddr).body(BodyInserters.fromFormData(mapBody)).retrieve().bodyToMono(String.class).block();

                            JsonNode nodeDocumentImgs = objMapper.readTree(strResult);

                            if (nodeDocumentImgs.get("result").asText().equals("success")) {
                                JsonNode nodeListImg = objMapper.readTree(nodeDocumentImgs.get("message").asText());

                                String strDocImg = nodeListImg.get(0).asText();

                                if (strDocImg.contains("_")) {
                                    HashMap<Integer, List<String>> hashMap = new HashMap<>();
                                    for (JsonNode nodeDocImg : nodeListImg) {
                                        String[] astrDocImg = nodeDocImg.asText().split("_");

                                        if (hashMap.containsKey(Integer.valueOf(astrDocImg[0]))) {
                                            hashMap.get(Integer.valueOf(astrDocImg[0])).add(astrDocImg[1]);
                                        } else {
                                            List<String> lstPage = new ArrayList<>();
                                            lstPage.add(astrDocImg[1]);
                                            hashMap.put(Integer.valueOf(astrDocImg[0]), lstPage);
                                        }
                                    }

                                    int countPage = 0;
                                    for (int i = 1; i <= hashMap.size(); i++) {
                                        List<String> lstPage = hashMap.get(i);

                                        if (lstPage != null) {
                                            for (String strPage : lstPage) {
                                                String strResourceImg = i + "_" + strPage;
                                                String strResourceImgAddr = Constants.SERVER_ADDR_CPU + "/img/get/doc";

                                                mapBody.clear();
                                                mapBody.add("uuid", _strUuid);
                                                mapBody.add("file", strFileName);
                                                mapBody.add("filename", strResourceImg);

                                                byte[] byResourceImg;
                                                byResourceImg = webClient.post().uri(strResourceImgAddr).body(BodyInserters.fromFormData(mapBody)).accept(MediaType.MULTIPART_FORM_DATA).retrieve().bodyToMono(byte[].class).block();

                                                if (byResourceImg != null) {
                                                    int nName = countPage + Integer.valueOf(strPage);
                                                    FileOutputStream fosImgData = new FileOutputStream(strImgPath + "/" + nName + ".jpg");
                                                    fosImgData.write(byResourceImg);
                                                    fosImgData.close();
                                                }
                                            }

                                            countPage += lstPage.size();
                                        }
                                    }
                                } else {
                                    for (JsonNode nodeDocImg : nodeListImg) {
                                        String strResourceImg = nodeDocImg.asText();
                                        String strResourceImgAddr = Constants.SERVER_ADDR_CPU + "/img/get/doc";

                                        mapBody.clear();
                                        mapBody.add("uuid", _strUuid);
                                        mapBody.add("file", strFileName);
                                        mapBody.add("filename", strResourceImg);

                                        byte[] byResourceImg;
                                        byResourceImg = webClient.post().uri(strResourceImgAddr).body(BodyInserters.fromFormData(mapBody)).accept(MediaType.MULTIPART_FORM_DATA).retrieve().bodyToMono(byte[].class).block();

                                        if (byResourceImg != null) {
                                            FileOutputStream fosImgData = new FileOutputStream(strImgPath + "/" + nodeDocImg.asText() + ".jpg");
                                            fosImgData.write(byResourceImg);
                                            fosImgData.close();
                                        }
                                    }
                                }
                            }

                            FileOutputStream fosFileData = new FileOutputStream(strJsonPath + "/result.json");
                            String strFileData = objMapper.writeValueAsString(fileData);
                            fosFileData.write(strFileData.getBytes());
                            fosFileData.close();

                        }
                    }

                    fileDirs = fileDir.listFiles(File::isDirectory);

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

                    StructDparserRes structDparserResResult = new StructDparserRes();
                    structDparserResResult.result = "success";
                    structDparserResResult.id = _strUuid;
                    structDparserResResult.message = objMapper.writeValueAsString(dictResult);

                    // 삭제 처리
                    String strDeleteDataAddr = Constants.SERVER_ADDR_CPU + "/data/delete/" + _strUuid;
                    webClient.delete().uri(strDeleteDataAddr).retrieve().bodyToMono(String.class).block();

                    lstDocFiles.clear();

                    return structDparserResResult;

                } catch (Exception e) {
                    StructDparserRes structDparserResResult = new StructDparserRes();
                    structDparserResResult.result = "failure";
                    structDparserResResult.id = _strUuid;
                    structDparserResResult.message = e.getMessage();

                    return structDparserResResult;
                }
            } else {
                return structDparserRes;
            }
        } catch (Exception e) {
            StructDparserRes structDparserResResult = new StructDparserRes();
            structDparserResResult.result = "failure";
            structDparserResResult.id = _strUuid;
            structDparserResResult.message = e.getMessage();

            return structDparserResResult;
        }
    }
}
