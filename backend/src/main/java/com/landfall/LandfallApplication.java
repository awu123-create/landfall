package com.landfall;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LandfallApplication {

    public static void main(String[] args) {
        SpringApplication.run(LandfallApplication.class, args);
    }
}
