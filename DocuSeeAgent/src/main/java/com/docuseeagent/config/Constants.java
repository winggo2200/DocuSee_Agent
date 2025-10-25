package com.docuseeagent.config;

public class Constants {
    // 지난 시간 삭제 처리를 위함
    public static final String REDIS_KEY_UPLOAD = "uploaded";
    // 대기중인 항목을 위함 - 갑자기 중단되었을 경우 복구용
    public static final String REDIS_KEY_WAIT = "waiting";
    // 처리중인 항목을 위함 - 갑자기 중단되었을 경우 복구용
    public static final String REDIS_KEY_PROC = "processing";
    // 지난 시간 삭제 처리를 위함
    public static final String REDIS_KEY_COMPLETED = "completed";

    // parsing 업로드
    public static final String REDIS_STATUS_UPLOAD = "uploaded";
    // parsing 대기중
    public static final String REDIS_STATUS_WAIT = "waiting";
    // parsing 진행중
    public static final String REDIS_STATUS_PROC = "processing";
    // parsing 완료
    public static final String REDIS_STATUS_COMPLETED = "completed";

    // 파일 보관 제한 시간
    public static final int TIMEOUT_HOUR_DOC = 3;


    //public static String SERVER_TYPE = System.getenv("SERVER_TYPE");

    public static String SERVER_ADDR_GPU = "http://218.145.184.155:33002";
    public static String SERVER_MAIN_HOST = "";
    public static String SERVER_ADDR_CPU = "http://localhost:8081/api/v2/dparser"; // Default value for local testing
    //public static String SERVER_DOC_IMG = (SERVER_AGENT + "/api/v2/agent/img/get/doc?");
    public static String SERVER_ADDR_IMG = "http://218.145.184.155:9000";
    public static String SERVER_API_KEY = "";

    public static final String PATH_DOC = "./doc/";
    public static final String PATH_RESULT = "./result/";

    public static void Initailization() {
        SERVER_MAIN_HOST = System.getenv("SERVER_MAIN_HOST");
        SERVER_ADDR_CPU = System.getenv("SERVER_ADDR_CPU") + "/api/v1/dparser";
        SERVER_ADDR_GPU = System.getenv("SERVER_ADDR_GPU");
        SERVER_ADDR_IMG = System.getenv("SERVER_ADDR_IMG");
    }
}
