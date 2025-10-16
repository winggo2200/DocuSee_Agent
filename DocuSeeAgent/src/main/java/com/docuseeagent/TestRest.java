package com.docuseeagent;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestRest {
    @PostMapping("/param-test")
    public ResponseEntity postTest(@RequestParam("data") String data){
        System.out.println("Received data: " + data);
        return new ResponseEntity(data, HttpStatus.OK);
    }
}
