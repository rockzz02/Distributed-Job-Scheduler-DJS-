package com.djs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DjsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DjsApplication.class, args);
    }
}
