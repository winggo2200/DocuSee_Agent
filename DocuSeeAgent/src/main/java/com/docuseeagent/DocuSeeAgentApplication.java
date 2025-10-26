package com.docuseeagent;

import com.docuseeagent.config.Constants;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class DocuSeeAgentApplication {

    public static void main(String[] args) {
        Constants.Initailization();
        SpringApplication.run(DocuSeeAgentApplication.class, args);
    }

}
