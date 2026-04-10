package com.chillchat;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@MapperScan("com.chillchat.mapper")
public class ChillChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChillChatApplication.class, args);
    }

}
