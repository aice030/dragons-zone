package com.dragons.core;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot 主应用类
 * 项目的入口类，用于启动整个应用
 * 
 * @author aice
 * @since 2026-01-15
 */
@SpringBootApplication
@MapperScan("com.dragons.core.dao")
public class DragonsCoreServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(DragonsCoreServerApplication.class, args);
    }

}
