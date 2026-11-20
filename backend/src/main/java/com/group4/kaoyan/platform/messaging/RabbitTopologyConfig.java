package com.group4.kaoyan.platform.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑：业务直连交换机 + 文档解析队列 + 死信交换机/队列。
 *
 * <p>文档解析队列带 {@code x-dead-letter-exchange}，消费重试超过上限后 reject 进死信，
 * 不再无限 requeue。旧队列 {@code doc_parse_queue} 已弃用，可在 broker 上手工删除。</p>
 */
@Configuration
public class RabbitTopologyConfig {
    public static final String EXCHANGE = "kaoyan.direct";

    // 文档自带测试队列
    public static final String TEST_QUEUE = "foundation.test.queue";
    public static final String TEST_ROUTING_KEY = "foundation.test";

    // 文档解析业务队列（新队列名，带死信参数；旧 doc_parse_queue 不再声明）
    public static final String DOC_PARSE_QUEUE = "kaoyan.doc.parse.queue";
    public static final String DOC_PARSE_ROUTING_KEY = "doc.parse.task";

    // 死信
    public static final String DLX_EXCHANGE = "kaoyan.dlx";
    public static final String DLQ_QUEUE = "kaoyan.doc.parse.dlq";
    public static final String DLQ_ROUTING_KEY = "doc.parse.dead";


    @Bean
    public DirectExchange kaoyanExchange() {
        return new DirectExchange(EXCHANGE);
    }

    /** DLX 必须先声明，队列参数引用它才不会报 not_found。 */
    @Bean
    public DirectExchange kaoyanDlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue foundationTestQueue() {
        return new Queue(TEST_QUEUE);
    }

    @Bean
    public Queue docParseQueue() {
        return QueueBuilder.durable(DOC_PARSE_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue docParseDeadLetterQueue() {
        return new Queue(DLQ_QUEUE);
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

    @Bean
    public Binding docParseDeadLetterBinding(Queue docParseDeadLetterQueue, DirectExchange kaoyanDlxExchange) {
        return BindingBuilder
                .bind(docParseDeadLetterQueue)
                .to(kaoyanDlxExchange)
                .with(DLQ_ROUTING_KEY);
    }
}
