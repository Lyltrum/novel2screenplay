package com.novel2screenplay.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 只负责：装配 ChatClient Bean。
 * ChatClient.Builder 由 spring-ai-alibaba-starter-dashscope 自动配置注入（已绑定 Qwen 模型）。
 */
@Configuration
public class ChatClientConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
