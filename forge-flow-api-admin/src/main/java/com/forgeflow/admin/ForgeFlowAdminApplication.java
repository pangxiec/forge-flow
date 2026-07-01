package com.forgeflow.admin;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.forgeflow.dao.mapper")
@SpringBootApplication(scanBasePackages = "com.forgeflow")
public class ForgeFlowAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(ForgeFlowAdminApplication.class, args);
    }
}
