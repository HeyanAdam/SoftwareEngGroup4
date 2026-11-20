package com.group4.kaoyan;

import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.group4.kaoyan.platform.messaging.RabbitMessagePublisher;
import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * MQ 单元测试（CI 稳定可跑）。
 *
 * <p>说明：本测试不启动 Spring 容器，也不连接真实 RabbitMQ，
 * 只用 Mockito 校验 {@link RabbitMessagePublisher} 的发送逻辑
 * （交换机名、routing key、消息内容是否正确）。
 * 因此 GitHub Actions 中没有 MySQL / Redis / RabbitMQ 也能通过。</p>
 *
 * <p>真实消息收发验证见 {@code MqIntegrationTest}（仅本地手动执行）。</p>
 */
@ExtendWith(MockitoExtension.class)
class MqTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RabbitMessagePublisher publisher;

    @BeforeEach
    void setUp() {
        // 直接 new，不依赖 Spring 容器
        publisher = new RabbitMessagePublisher(rabbitTemplate);
    }

    @Test
    @DisplayName("测试队列发送：交换机、routing key、消息体正确")
    void testSendTestMessage() {
        String expectMsg = "rabbitmq ok";

        publisher.sendTestMessage(expectMsg);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitTopologyConfig.EXCHANGE),
                eq(RabbitTopologyConfig.TEST_ROUTING_KEY),
                eq(expectMsg)
        );
    }

    @Test
    @DisplayName("文档解析任务发送：交换机、routing key、DTO 正确")
    void testSendDocParseTask() {
        DocParseTaskDTO task = new DocParseTaskDTO();
        task.setDocId(1L);
        task.setFileName("测试文档.pdf");
        task.setFileUrl("http://localhost:9000/kaoyan-files/test/test.pdf");

        publisher.sendDocParseTask(task);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitTopologyConfig.EXCHANGE),
                eq(RabbitTopologyConfig.DOC_PARSE_ROUTING_KEY),
                eq(task)
        );
    }

    @Test
    @DisplayName("拓扑常量与队列命名符合约定")
    void testTopologyConstants() {
        RabbitTopologyConfig topology = new RabbitTopologyConfig();

        // 交换机、队列名与文档约定一致
        assertTopologyNames();

        assertEquals(RabbitTopologyConfig.TEST_QUEUE, topology.foundationTestQueue().getName());
        assertEquals(RabbitTopologyConfig.DOC_PARSE_QUEUE, topology.docParseQueue().getName());
        assertEquals(RabbitTopologyConfig.EXCHANGE, topology.kaoyanExchange().getName());
        assertEquals(RabbitTopologyConfig.DLX_EXCHANGE, topology.kaoyanDlxExchange().getName());
        assertEquals(RabbitTopologyConfig.DLQ_QUEUE, topology.docParseDeadLetterQueue().getName());
    }

    private void assertTopologyNames() {
        assertEquals("kaoyan.direct", RabbitTopologyConfig.EXCHANGE);
        assertEquals("foundation.test.queue", RabbitTopologyConfig.TEST_QUEUE);
        assertEquals("foundation.test", RabbitTopologyConfig.TEST_ROUTING_KEY);
        assertEquals("kaoyan.doc.parse.queue", RabbitTopologyConfig.DOC_PARSE_QUEUE);
        assertEquals("doc.parse.task", RabbitTopologyConfig.DOC_PARSE_ROUTING_KEY);
        assertEquals("kaoyan.dlx", RabbitTopologyConfig.DLX_EXCHANGE);
        assertEquals("kaoyan.doc.parse.dlq", RabbitTopologyConfig.DLQ_QUEUE);
        assertEquals("doc.parse.dead", RabbitTopologyConfig.DLQ_ROUTING_KEY);
    }
}
