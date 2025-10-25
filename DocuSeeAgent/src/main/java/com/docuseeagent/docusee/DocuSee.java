package com.docuseeagent.docusee;

import com.docuseeagent.config.Constants;
import com.docuseeagent.model.parser.ParserRes;
import com.docuseeagent.model.redis.RedisDataInfo;
import com.docuseeagent.service.RedisService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
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
import reactor.netty.http.client.HttpClient;

import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Component
public class DocuSee implements HealthIndicator {
    public static ParserRes Parse(String _strUuid, RedisService _redisService) {
        String strFilePath = new File(Constants.PATH_DOC).getAbsolutePath() + "/" + _strUuid + "/GPU";
        File[] fileList = new File(strFilePath).listFiles(File::isFile);

        ObjectMapper objMapper = new ObjectMapper();

        try {
            if (fileList != null) {
                if (fileList.length > 0) {

                    String strToken = GetToken();

                    if(strToken.isEmpty())
                        throw new Exception();

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

                        String strRes = webClient.post().uri(strUploadProcUrl).header("Authorization", strToken)
                                .body(BodyInserters.fromMultipartData(multipartBodyBuilderForGPU.build())).retrieve().bodyToMono(String.class).block();


                        JsonNode nodeResult = objMapper.readTree(strRes);

                        String strTaskId = nodeResult.get("task_id").asText();

                        dictFileTaskId.put(strTaskId, file.getName());
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
                            structParserRes.result = "failure";
                            structParserRes.id = _strUuid;
                            structParserRes.message = e.getMessage();

                            return structParserRes;
                        }
                    }



                    //HashMap<String, String> dictResults = new HashMap<>();

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

                                    JsonNode nodePages = nodeParseResult.get("analyze_results");
                                    //JsonNode nodePages = nodeParseResult.get("results");

                                    SslContext sslContext = SslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).build();
                                    HttpClient httpClient = HttpClient.create().secure(t -> t.sslContext(sslContext));

                                    WebClient webclientForImg = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                                            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                                            .build()).clientConnector(new ReactorClientHttpConnector(httpClient)).build();

                                    List<Object> lstResults = new ArrayList<>();
                                    List<Object> lstDocImg = new ArrayList<>();

                                    for (JsonNode nodePage : nodePages) {
                                        String strPageNum = nodePage.get("page_index").asText();
                                        //String strPageNum = nodePage.get("page").asText();




                                        //String strPageJson = nodePage.get("json_data").asText();


                                        //String strPageJson = nodePage.get("result").asText();
                                        JsonNode nodePagesData = nodePage.get("analyze_result");
                                        String strPageJson = nodePagesData.asText();

                                        FileOutputStream fos1 = new FileOutputStream(strJsonPath + "/" + strPageNum + ".json");
                                        fos1.write(strPageJson.getBytes());
                                        fos1.close();

                                        //JsonNode nodePagesData = objMapper.readTree(strPageJson);


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

                                    dictStatusDocParse.put(strFileName, "success");

                                    break;
                                }else if(nodeParseResult.get("status").asText().equals("EXPIRED") || nodeParseResult.get("status").asText().equals("FAILURE")) {
                                    dictStatusDocParse.put(strFileName, nodeParseResult.get("status").asText());

                                    break;
                                }else {
                                    Thread.sleep(1000);

                                    long stopTime = System.currentTimeMillis();
                                    long elapsedTime = stopTime - startTime;

                                    if (elapsedTime > 3600000) {
                                        dictStatusDocParse.put(strFileName, nodeParseResult.get("status").asText());

                                        break;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            ParserRes structParserRes = new ParserRes();
                            structParserRes.result = "failure";
                            structParserRes.id = _strUuid;
                            structParserRes.message = e.getMessage();

                            return structParserRes;
                        }
                    }

                    ParserRes structParserRes = new ParserRes();
                    structParserRes.result = "success";
                    structParserRes.id = _strUuid;
                    structParserRes.message = objMapper.writeValueAsString(dictStatusDocParse);

                    return structParserRes;
                }
            }
        } catch (Exception e) {
            ParserRes structParserRes = new ParserRes();
            structParserRes.result = "failure";
            structParserRes.id = _strUuid;
            structParserRes.message = e.getMessage();

            return structParserRes;
        }

        ParserRes structParserRes = new ParserRes();
        structParserRes.result = "failure";
        structParserRes.id = _strUuid;
        structParserRes.message = "Not found files to parse";

        return structParserRes;
    }

    private static String GetToken() {
        MultiValueMap<String, String> mapData = new LinkedMultiValueMap<>();
        mapData.add("username", "GenApp");
        mapData.add("password", "AppPW@!");

        WebClient webClient = WebClient.builder().build();
        String url = Constants.SERVER_ADDR_GPU + "/api/v1/users/login";

        String strRes = webClient.post().uri(url).body(BodyInserters.fromFormData(mapData)).retrieve().bodyToMono(String.class).block();

        ObjectMapper objMapper = new ObjectMapper();

        try {
            JsonNode nodeRoot = objMapper.readTree(strRes);

            String  strToken = "bearer " + nodeRoot.get("access_token").asText();

            return strToken;
        } catch (Exception e) {
            return "";
        }
    }

    @Override
    public Health health() {
        WebClient webClient = WebClient.builder().exchangeStrategies(ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(-1))
                .build()).build();

        return Health.up().withDetail("DocuSee", "UnAvailable").build();
    }
}
