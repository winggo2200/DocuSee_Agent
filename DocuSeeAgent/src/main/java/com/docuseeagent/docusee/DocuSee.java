package com.docuseeagent.docusee;

import com.docuseeagent.config.Constants;
import com.docuseeagent.model.parser.ParserRes;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.util.http.fileupload.FileUtils;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Slf4j
@Component
public class DocuSee implements HealthIndicator {
    public static ParserRes Parse(String _strUuid, RedisService _redisService) {
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/GPU";
        File[] fileList = new File(strFilePath).listFiles(File::isFile);

        ObjectMapper objMapper = new ObjectMapper();

        try {
            if (fileList != null) {
                if (fileList.length > 0) {
                    String strUploadProcUrl = Constants.SERVER_ADDR_GPU + "/api/v1/images/upload_and_process_async";

                    WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                            .build()).build();

                    HashMap<String, String> dictFileTaskId = new HashMap<>();

                    HashMap<String, String> dictStatusDocParse = new HashMap<>();
                    for (File file : fileList) {
                        dictStatusDocParse.put(file.getName(), "failure");
                    }

                    for (File file : fileList) {
                        MultipartBodyBuilder multipartBodyBuilderForGPU = new MultipartBodyBuilder();

                        Resource resourceFile = new FileSystemResource(file);
                        multipartBodyBuilderForGPU.part("file", resourceFile);
                        multipartBodyBuilderForGPU.part("isChartChecked", "False");

//                        String strRes = webClient.post().uri(strUploadProcUrl)
//                                .body(BodyInserters.fromMultipartData(multipartBodyBuilderForGPU.build())).retrieve().bodyToMono(String.class).block();

                        try {
                            String strRes = webClient.post().uri(strUploadProcUrl)
                                    .body(BodyInserters.fromMultipartData(multipartBodyBuilderForGPU.build())).retrieve().bodyToMono(String.class).block();

                            JsonNode nodeResult = objMapper.readTree(strRes);

                            String strTaskId = nodeResult.get("task_id").asText();

                            dictFileTaskId.put(strTaskId, file.getName());

                        } catch (Exception e) {

                            ParserRes structParserRes = new ParserRes();
                            structParserRes.status = "failure";
                            structParserRes.id = _strUuid;
                            structParserRes.message = e.getMessage();

                            log.info(objMapper.writeValueAsString(structParserRes));

                            return structParserRes;

                        }
                    }

                    if(_redisService != null) {
                        String strData = _redisService.GetValue(_strUuid);
                        ObjectMapper objectMapper = new ObjectMapper();
                        try {
                            RedisDataInfo redisData = objectMapper.readValue(strData, RedisDataInfo.class);

                            //redisData.docuseeTaskIds = List.copyOf(dictFileTaskId.keySet());

                            _redisService.SetValue(_strUuid, objectMapper.writeValueAsString(redisData));
                        } catch (Exception e) {
                            _redisService.RemoveListValue(Constants.REDIS_KEY_PROC, _strUuid);
                            _redisService.DeleteValue(_strUuid);

                            ParserRes structParserRes = new ParserRes();
                            structParserRes.status = "failure";
                            structParserRes.id = _strUuid;
                            structParserRes.message = e.getMessage();

                            log.error(objectMapper.writeValueAsString(structParserRes));

                            return structParserRes;
                        }
                    }

                    //HashMap<String, String> dictResults = new HashMap<>();

                    boolean bSuccess = false;

                    for (String strTask : dictFileTaskId.keySet()) {
                        try {
                            String strUrl = Constants.SERVER_ADDR_GPU + "/api/v1/images/get_task_results/" + strTask;

                            long startTime = System.currentTimeMillis();

                            while (true) {
                                String strRes = webClient.get().uri(strUrl).retrieve().bodyToMono(String.class).block();

                                JsonNode nodeParseResult = objMapper.readTree(strRes);

                                String strFileName = dictFileTaskId.get(strTask);

                                if (nodeParseResult.get("status").asText().equals("SUCCESS")) {
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

                                    //JsonNode nodePages = nodeParseResult.get("analyze_results");
                                    JsonNode nodePages = nodeParseResult.get("results");

                                    SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                                    HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

                                    WebClient webclientForImg = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                                            .build()).clientConnector(new ReactorClientHttpConnector(httpClient)).build();

                                    List<Object> lstResults = new ArrayList<>();
                                    List<Object> lstDocImg = new ArrayList<>();

                                    List<Object> lstResourceImgs = new ArrayList<>();

                                    for (JsonNode nodePage : nodePages) {
                                        String strPageNum = nodePage.get("page").asText();

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
                                        dictAnalyData.put("charts", nodePagesData.get("charts"));
                                        dictAnalyData.put("diagrams", nodePagesData.get("diagrams"));
                                        dictAnalyData.put("complexity", nodePagesData.get("complexity"));

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

                                        String strDocImgAddr = Constants.SERVER_MAIN_HOST + "/api/v2/agent/img/get/doc?uuid=" + _strUuid + "&file=" + strDocFileName + "&page=" + strPageNum;

                                        HashMap<String, String> dictDocImg = new HashMap<>();
                                        dictDocImg.put("src", strDocImgAddr);
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


                                        for(JsonNode nodeResource : nodePagesData.get("image_urls")){
                                            byPageImg = webclientForImg.get().uri(nodeResource.get("src").asText()).accept(MediaType.MULTIPART_FORM_DATA).retrieve().bodyToMono(byte[].class).block();

                                            FileOutputStream fosImg = new FileOutputStream(strJsonPath + "/" + nodeResource.get("key").asText());
                                            fosImg.write(byPageImg);
                                            fosImg.close();

                                            // strDocFileName - Encoded
                                            String strResourceUrl = Constants.SERVER_MAIN_HOST + "/api/v2/agent/img/get/resource?uuid=" + _strUuid + "&file=" + strDocFileName + "&filename=" + nodeResource.get("key").asText();

                                            HashMap<String, String> dictResourceImg = new HashMap<>();
                                            dictResourceImg.put("src", strResourceUrl);
                                            dictResourceImg.put("key", nodeResource.get("key").asText());

                                            lstResourceImgs.add(dictResourceImg);
                                        }
                                    }

                                    HashMap<String, Object> dictResultDoc = new HashMap<>();

                                    dictResultDoc.put("filename", dictFileTaskId.get(strTask));
                                    dictResultDoc.put("pages", lstResults);
                                    dictResultDoc.put("page_image_urls", lstDocImg);
                                    dictResultDoc.put("image_urls", lstResourceImgs);

                                    FileOutputStream fosFileData = new FileOutputStream(strJsonPath + "/result.json");
                                    String strFileData = objMapper.writeValueAsString(dictResultDoc);
                                    fosFileData.write(strFileData.getBytes());
                                    fosFileData.close();

                                    dictStatusDocParse.put(strFileName, "success");

                                    bSuccess = true;

                                    log.info(_strUuid + " - " + strFileName + " - parsing " + nodeParseResult.get("status").asText());

                                    break;
                                }else if(nodeParseResult.get("status").asText().equals("EXPIRED") || nodeParseResult.get("status").asText().equals("FAILURE")) {
                                    dictStatusDocParse.put(strFileName, nodeParseResult.get("status").asText());

//                                    ParserRes structParserRes = new ParserRes();
//                                    structParserRes.result = "failure";
//                                    structParserRes.id = _strUuid;
//                                    structParserRes.message = strFileName + " - parsing " + nodeParseResult.get("status").asText();

                                    log.info(_strUuid + " - " + strFileName + " - parsing " + nodeParseResult.get("status").asText() + " - " + nodeParseResult.get("message"));

                                    break;
                                }else {
                                    Thread.sleep(1000);

                                    long stopTime = System.currentTimeMillis();
                                    long elapsedTime = stopTime - startTime;

                                    if (elapsedTime > 3600000) {
                                        dictStatusDocParse.put(strFileName, nodeParseResult.get("status").asText());

//                                        ParserRes structParserRes = new ParserRes();
//                                        structParserRes.result = "failure";
//                                        structParserRes.id = _strUuid;
//                                        structParserRes.message = strFileName + " - parsing : timeout";

                                        log.info(objMapper.writeValueAsString(_strUuid + " - " + strFileName + " - parsing : timeout"));

                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            ParserRes structParserRes = new ParserRes();
                            structParserRes.status = "failure";
                            structParserRes.id = _strUuid;
                            structParserRes.message = e.getMessage();

                            log.info(objMapper.writeValueAsString(structParserRes));

                            return structParserRes;
                        }
                    }

                    ParserRes structParserRes = new ParserRes();

                    if(bSuccess) structParserRes.status = "success";
                    else structParserRes.status = "failure";
                    structParserRes.id = _strUuid;
                    structParserRes.message = objMapper.writeValueAsString(dictStatusDocParse);

                    log.info(objMapper.writeValueAsString(structParserRes));

                    return structParserRes;
                }
            }
        } catch (Exception e) {
            ParserRes structParserRes = new ParserRes();
            structParserRes.status = "failure";
            structParserRes.id = _strUuid;
            structParserRes.message = e.getMessage();

            try {
                log.info(objMapper.writeValueAsString(structParserRes));
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }

            return structParserRes;
        }

        ParserRes structParserRes = new ParserRes();
        structParserRes.status = "failure";
        structParserRes.id = _strUuid;
        structParserRes.message = "Not found files to parse";

        try {
            log.info(objMapper.writeValueAsString(structParserRes));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        return structParserRes;
    }

    @Override
    public Health health() {
        WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build()).build();

        String strDocuseeAddr = Constants.SERVER_ADDR_GPU + "/api/v1/health";

        try {
            String strResult = webClient.get().uri(strDocuseeAddr).retrieve().bodyToMono(String.class).timeout(Duration.ofMillis(500)).block();

            if (strResult.equals("\"OK\"")) {
                return Health.up().withDetail("DocuSee", "Available").build();
            }else{
                return Health.down().withDetail("DocuSee", "UnAvailable").build();
            }

        } catch (Exception e) {
            return Health.down().withDetail("DocuSee", "UnAvailable").build();
        }



    }
}
