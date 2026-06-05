package com.novel2screenplay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live 冒烟测试：验证 Spring AI 结构化输出 .entity() 能让 Qwen 吐出强类型对象。
 * 会真实调用模型、消耗 token，因此默认禁用——仅当显式传 -Dlive=true 时运行：
 *   mvn test -Dlive=true -Dtest=QwenStructuredOutputLiveTest
 * 需要 application-local.yml 里已配置可用的 dashscope api-key（默认 local 档加载）。
 */
@SpringBootTest
@EnabledIfSystemProperty(named = "live", matches = "true")
class QwenStructuredOutputLiveTest {

    /** 临时的玩具结构，仅用于验证「.entity() 能把模型输出反序列化成 record」。 */
    record Person(String name, int age) {
    }

    @Autowired
    private ChatClient chatClient;

    @Test
    void extractsStructuredObjectFromQwen() {
        Person person = chatClient.prompt()
                .user("从这句话中抽取人物的姓名与年龄：李白今年三十岁。")
                .call()
                .entity(Person.class);

        System.out.println("===== Qwen 结构化抽取结果 ===== " + person);

        assertThat(person).isNotNull();
        assertThat(person.name()).contains("李白");
        assertThat(person.age()).isEqualTo(30);
    }
}
