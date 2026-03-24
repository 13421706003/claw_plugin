package com.hsd;

import com.hsd.config.DashScopeAsrProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(DashScopeAsrProperties.class)
public class OpenHSDApplication {
    public static void main(String[] args) {
        SpringApplication.run(OpenHSDApplication.class, args);
    }
}
