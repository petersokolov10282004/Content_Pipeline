package com.contentpipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ContentPipelineApplication {
    public static void main(String[] args) {
        SpringApplication.run(ContentPipelineApplication.class, args);
    }
}
