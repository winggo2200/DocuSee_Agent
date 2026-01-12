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

    // 엑셀의 셀 수 제한
    public static final long LIMIT_CELLS = 80000;

    public static String SERVER_ADDR_GPU = "";
    public static String SERVER_MAIN_HOST = "";
    public static String SERVER_ADDR_CPU = ""; // Default value for local testing
    public static String SERVER_ADDR_IMG = "";
    public static String SERVER_API_KEY = "";

    public static final String PATH_DOC = "./doc/";
    public static final String PATH_RESULT = "./result/";

    public static void Initailization() {
        SERVER_MAIN_HOST = System.getenv("SERVER_MAIN_HOST");
        SERVER_ADDR_CPU = System.getenv("SERVER_ADDR_CPU");
        SERVER_ADDR_GPU = System.getenv("SERVER_ADDR_GPU");
        SERVER_ADDR_IMG = System.getenv("SERVER_ADDR_IMG");

        // Default
        if(SERVER_MAIN_HOST == null) SERVER_MAIN_HOST = "http://localhost:8080";

        if(SERVER_ADDR_CPU == null) SERVER_ADDR_CPU = "http://localhost:8081/api/v2/dparser";
        else SERVER_ADDR_CPU += "/api/v2/dparser";

        if(SERVER_ADDR_GPU == null) SERVER_ADDR_GPU = "http://localhost:33002";

        if(SERVER_ADDR_IMG == null) SERVER_ADDR_IMG = "http://localhost:9000";
    }
}
