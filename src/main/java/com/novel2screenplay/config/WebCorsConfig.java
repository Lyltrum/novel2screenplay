package com.novel2screenplay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 仅为可选 demo 前端（web/index.html）联调用的跨域配置，非课题要求，删除不影响主干。
 * 放开 /api/** 的跨域访问，并暴露 X-Validation-Warnings 头供前端读取。
 */
@Configuration
public class WebCorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("X-Validation-Warnings");
    }
}
