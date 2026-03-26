package com.dietbuilder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DietBuilderApplication {
    public static void main(String[] args) {
        SpringApplication.run(DietBuilderApplication.class, args);
    }
}
