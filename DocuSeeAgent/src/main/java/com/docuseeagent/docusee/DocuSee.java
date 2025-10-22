package com.docuseeagent.docusee;

import com.docuseeagent.config.Constants;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DocuSee {
    public static String Parse(String _strUuid, RedisService _redisService) {
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/GPU";
        File[] fileList = new File(strFilePath).listFiles(File::isFile);

        try {
            if (fileList != null) {
                if (fileList.length > 0) {
                    String strUploadProcUrl = Constants.SERVER_ADDR_GPU + "/api/v1/images/upload_and_process_async";

                    WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                            .build()).build();

                    HashMap<String, String> dictFileTaskId = new HashMap<>();

                    ObjectMapper objMapper = new ObjectMapper();

                    for (File file : fileList) {
                        MultipartBodyBuilder multipartBodyBuilderForGPU = new MultipartBodyBuilder();

                        Resource resourceFile = new FileSystemResource(file);
                        multipartBodyBuilderForGPU.part("file", resourceFile);
                        multipartBodyBuilderForGPU.part("isChartChecked", "False");

                        String strRes = webClient.post().uri(strUploadProcUrl)
                                .body(BodyInserters.fromMultipartData(multipartBodyBuilderForGPU.build())).retrieve().bodyToMono(String.class).block();

                        JsonNode nodeResult = objMapper.readTree(strRes);

                        String strTaskId = nodeResult.get("task_id").asText();

                        dictFileTaskId.put(strTaskId, file.getName());
                    }



                    String strData = _redisService.GetValue(_strUuid);
                    ObjectMapper objectMapper = new ObjectMapper();
                    try {
                        RedisDataInfo redisData = objectMapper.readValue(strData, RedisDataInfo.class);

                        redisData.docuseeTaskIds = List.copyOf(dictFileTaskId.keySet());

                        _redisService.SetValue(_strUuid, redisData);
                    } catch (Exception e) {
                        _redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                        _redisService.DeleteValue(_strUuid);
                        return null;
                    }



                    for (String strTask : dictFileTaskId.keySet()) {
                        try {
                            String strUrl = Constants.SERVER_ADDR_GPU + "/api/v1/images/get_task_results/" + strTask;

                            long startTime = System.currentTimeMillis();

                            while (true) {
                                String strRes = webClient.get().uri(strUrl).retrieve().bodyToMono(String.class).block();

                                JsonNode nodeParseResult = objMapper.readTree(strRes);

                                if (nodeParseResult.get("status").asText().equals("SUCCESS")) {
                                    String strFileName = dictFileTaskId.get(strTask);
                                    String strFileFirst = strFileName.substring(0, strFileName.lastIndexOf("."));
                                    String strFileLast = strFileName.substring(strFileName.lastIndexOf(".") + 1);

                                    String strResultPath = new File(Constants.PATH_RESULT).getAbsolutePath() + "/" + _strUuid + "/" + strFileFirst + "_" + strFileLast + "/";

                                    String strJsonPath = strResultPath + "DATA";
                                    String strImgPath = strResultPath + "JPEG";

                                    FileUtils.forceMkdir(new File(strJsonPath));
                                    FileUtils.forceMkdir(new File(strImgPath));

                                    FileOutputStream fos_receive = new FileOutputStream(strJsonPath + "/receive.json");
                                    fos_receive.write(strRes.getBytes());
                                    fos_receive.close();

                                    JsonNode nodePages = nodeParseResult.get("results");

                                    SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                                    HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

                                    WebClient webclientForImg = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                                            .build()).clientConnector(new ReactorClientHttpConnector(httpClient)).build();

                                    List<Object> lstResults = new ArrayList<>();
                                    List<Object> lstDocImg = new ArrayList<>();

                                    for (JsonNode nodePage : nodePages) {
                                        String strPageNum = nodePage.get("page").asText();
                                        //String strPageJson = nodePage.get("json_data").asText();
                                        String strPageJson = nodePage.get("result").asText();

                                        FileOutputStream fos1 = new FileOutputStream(strJsonPath + "/" + strPageNum + ".json");
                                        fos1.write(strPageJson.getBytes());
                                        fos1.close();

                                        //JsonNode nodePagesData = objMapper.readTree(strPageJson);
                                        JsonNode nodePagesData = nodePage.get("result");

                                        HashMap<String, Object> dictDocData = new HashMap<>();

                                        HashMap<String, Object> dictAnalyData = new HashMap<>();
                                        dictAnalyData.put("content", nodePagesData.get("content"));
                                        dictAnalyData.put("markdown", nodePagesData.get("markdown"));
                                        dictAnalyData.put("width", nodePagesData.get("width"));
                                        dictAnalyData.put("height", nodePagesData.get("height"));
                                        dictAnalyData.put("unit", nodePagesData.get("unit"));
                                        dictAnalyData.put("paragraphs", nodePagesData.get("paragraphs"));
                                        //dictAnalyData.put("lines", nodePagesData.get("lines"));
                                        //dictAnalyData.put("chart", nodePagesData.get("chart"));

                                        //HashMap<String, Object> dictTable = new HashMap<>();
                                        List<Object> lstTables = new ArrayList<>();


                                        if (nodePagesData.get("tables") != null) {
                                            if (nodePagesData.get("tables").size() > 0) {
                                                for (JsonNode nodeTable : nodePagesData.get("tables")) {
                                                    //dictTable.put(String.valueOf(idx++), nodeTable);
                                                    lstTables.add(nodeTable);
                                                }
                                            }
                                        }

                                        dictAnalyData.put("table", lstTables);

                                        dictDocData.put("analyzeResult", dictAnalyData);
                                        dictDocData.put("status", "succeeded");

                                        lstResults.add(dictDocData);

                                        String strDocFileName = URLEncoder.encode(dictFileTaskId.get(strTask), StandardCharsets.UTF_8);

                                        String strImgAddr = Constants.SERVER_MAIN_HOST + "/api/v2/agent/img/get/doc?uuid=" + _strUuid + "&file=" + strDocFileName + "&page=" + strPageNum;

                                        HashMap<String, String> dictDocImg = new HashMap<>();
                                        dictDocImg.put("src", strImgAddr);
                                        dictDocImg.put("alt", "페이지" + strPageNum);

                                        lstDocImg.add(dictDocImg);

                                        String strPageImgUrl = nodePage.get("image_data").asText();

                                        String strPageImgName = strPageImgUrl.substring(strPageImgUrl.lastIndexOf("/") + 1);

                                        strPageImgUrl = Constants.SERVER_ADDR_IMG + "/dummy-user/" + strPageImgName;

                                        byte[] byPageImg;
                                        byPageImg = webclientForImg.get().uri(strPageImgUrl).accept(MediaType.MULTIPART_FORM_DATA).retrieve().bodyToMono(byte[].class).block();

                                        FileOutputStream fos2 = new FileOutputStream(strImgPath + "/" + strPageNum + ".jpg");
                                        fos2.write(byPageImg);
                                        fos2.close();
                                    }

                                    HashMap<String, Object> dictResultDoc = new HashMap<>();

                                    dictResultDoc.put("filename", dictFileTaskId.get(strTask));
                                    dictResultDoc.put("pages", lstResults);
                                    dictResultDoc.put("page_image_urls", lstDocImg);

                                    FileOutputStream fosFileData = new FileOutputStream(strJsonPath + "/all_data.json");
                                    String strFileData = objMapper.writeValueAsString(dictResultDoc);
                                    fosFileData.write(strFileData.getBytes());
                                    fosFileData.close();

                                    break;
                                } else {
                                    Thread.sleep(1000);

                                    long stopTime = System.currentTimeMillis();
                                    long elapsedTime = stopTime - startTime;

                                    if (elapsedTime > 3600000) {

                                        return null;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }

                    return _strUuid;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return null;
    }
}
