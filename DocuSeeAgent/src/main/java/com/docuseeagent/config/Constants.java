package com.docuseeagent.config;

public class Constants {
    public static String SERVER_TYPE = System.getenv("SERVER_TYPE");

    public static String SERVER_ADDR_GPU = "";

    public static String SERVER_MAIN_HOST = "";
    public static String SERVER_ADDR_CPU = "http://localhost:8081/api/v2/dparser"; // Default value for local testing
    public static String SERVER_ADDR_IMG = "";
    public static String SERVER_API_KEY = "";

    public static final String PATH_DOC = "./doc/";
    public static final String PATH_RESULT = "./result/";

    public static void Initailization() {
        if (SERVER_TYPE != null && !SERVER_TYPE.isEmpty()) {
            if (SERVER_TYPE.equals("DOC_CENTRAL")) {
                SERVER_API_KEY = System.getenv("SERVER_API_KEY");
                SERVER_ADDR_CPU = System.getenv("SERVER_ADDR_CPU") + "/api/v1/dparser";
                SERVER_ADDR_GPU = System.getenv("SERVER_ADDR_GPU");
            } else if (SERVER_TYPE.equals("GENX2")) {
                SERVER_MAIN_HOST = System.getenv("SERVER_MAIN_HOST");

                SERVER_ADDR_CPU = "http://" + System.getenv("DPARSER_SVC_SERVICE_HOST") + ":" + System.getenv("DPARSER_SVC_SERVICE_PORT") + "/api/v1/dparser";
                SERVER_ADDR_GPU = System.getenv("SERVER_ADDR_GPU");
                SERVER_ADDR_IMG = System.getenv("SERVER_ADDR_IMG");
            }
        }
//        else {
//            SERVER_TYPE = "Default";
//            SERVER_MAIN_HOST = System.getenv("SERVER_MAIN_HOST");
//            SERVER_ADDR_CPU = System.getenv("SERVER_ADDR_CPU") + "/api/v1/dparser";
//            SERVER_ADDR_GPU = System.getenv("SERVER_ADDR_GPU");
//            SERVER_ADDR_IMG = System.getenv("SERVER_ADDR_IMG");
//        }
    }
}
