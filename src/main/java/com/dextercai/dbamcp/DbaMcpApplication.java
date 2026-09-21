package com.dextercai.dbamcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DbaMcpApplication {
    public static void main(String[] args) {
        SpringApplication.run(DbaMcpApplication.class, args);
    }
}
