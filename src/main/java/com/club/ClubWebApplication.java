package com.club;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 智慧社团Web系统 启动类
 */
@SpringBootApplication
@MapperScan("com.club.mapper")
public class ClubWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClubWebApplication.class, args);
    }
}
