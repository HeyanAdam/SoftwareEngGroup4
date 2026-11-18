package com.group4.kaoyan.platform.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {
    public static final String EXCHANGE = "kaoyan.direct";

    // 文档自带测试队列
    public static final String TEST_QUEUE = "foundation.test.queue";
    public static final String TEST_ROUTING_KEY = "foundation.test";

    // 文档解析业务队列
    public static final String DOC_PARSE_QUEUE = "doc_parse_queue";
    public static final String DOC_PARSE_ROUTING_KEY = "doc.parse.task";


    @Bean
    public DirectExchange kaoyanExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue foundationTestQueue() {
        return new Queue(TEST_QUEUE);
    }

    @Bean
    public Queue docParseQueue() {
        return new Queue(DOC_PARSE_QUEUE);
    }

    @Bean
    public Binding foundationTestBinding(Queue foundationTestQueue, DirectExchange kaoyanExchange) {
        return BindingBuilder
                .bind(foundationTestQueue)
                .to(kaoyanExchange)
                .with(TEST_ROUTING_KEY);
    }

    @Bean
    public Binding docParseBinding(Queue docParseQueue, DirectExchange kaoyanExchange) {
        return BindingBuilder
                .bind(docParseQueue)
                .to(kaoyanExchange)
                .with(DOC_PARSE_ROUTING_KEY);
    }
}
