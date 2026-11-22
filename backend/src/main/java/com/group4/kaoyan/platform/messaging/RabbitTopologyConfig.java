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

    // 聊天回答队列（A-2）：带死信；失败先进延迟重试队列，30 秒后回到业务交换机
    public static final String CHAT_ANSWER_QUEUE = "kaoyan.chat.answer.queue";
    public static final String CHAT_ANSWER_ROUTING_KEY = "chat.answer.task";
    public static final String CHAT_RETRY_QUEUE = "kaoyan.chat.answer.retry.queue";
    public static final String CHAT_RETRY_ROUTING_KEY = "chat.answer.retry";
    public static final String CHAT_DLQ = "kaoyan.chat.answer.dlq";
    public static final String CHAT_DLQ_ROUTING_KEY = "chat.answer.dead";
    /** 重试队列消息 TTL：30 秒 */
    public static final int CHAT_RETRY_TTL_MILLIS = 30_000;

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

    @Bean
    public Queue chatAnswerQueue() {
        return QueueBuilder.durable(CHAT_ANSWER_QUEUE)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CHAT_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue chatAnswerDeadLetterQueue() {
        return new Queue(CHAT_DLQ);
    }

    /**
     * 延迟重试队列：消息在这里滞留 30 秒，到期由 x-dead-letter-* 重新投回
     * {@code kaoyan.direct / chat.answer.task}，形成"失败 → 等待 → 重试"闭环。
     */
    @Bean
    public Queue chatAnswerRetryQueue() {
        return QueueBuilder.durable(CHAT_RETRY_QUEUE)
                .withArgument("x-message-ttl", CHAT_RETRY_TTL_MILLIS)
                .withArgument("x-dead-letter-exchange", EXCHANGE)
                .withArgument("x-dead-letter-routing-key", CHAT_ANSWER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding chatAnswerBinding(Queue chatAnswerQueue, DirectExchange kaoyanExchange) {
        return BindingBuilder.bind(chatAnswerQueue).to(kaoyanExchange).with(CHAT_ANSWER_ROUTING_KEY);
    }

    @Bean
    public Binding chatAnswerRetryBinding(Queue chatAnswerRetryQueue, DirectExchange kaoyanDlxExchange) {
        return BindingBuilder.bind(chatAnswerRetryQueue).to(kaoyanDlxExchange).with(CHAT_RETRY_ROUTING_KEY);
    }

    @Bean
    public Binding chatAnswerDeadLetterBinding(Queue chatAnswerDeadLetterQueue, DirectExchange kaoyanDlxExchange) {
        return BindingBuilder.bind(chatAnswerDeadLetterQueue).to(kaoyanDlxExchange).with(CHAT_DLQ_ROUTING_KEY);
    }
}
