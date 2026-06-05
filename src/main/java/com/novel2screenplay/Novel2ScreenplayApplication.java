package com.novel2screenplay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** 应用启动入口，仅负责引导 Spring 容器。 */
@SpringBootApplication
public class Novel2ScreenplayApplication {

    public static void main(String[] args) {
        SpringApplication.run(Novel2ScreenplayApplication.class, args);
    }
}
