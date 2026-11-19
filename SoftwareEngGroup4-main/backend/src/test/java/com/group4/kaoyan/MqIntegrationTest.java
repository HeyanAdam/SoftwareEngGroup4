package com.group4.kaoyan;

import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.group4.kaoyan.platform.messaging.RabbitMessagePublisher;
import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * MQ 集成测试：连接真实 RabbitMQ，验证消息收发闭环。
 *
 * <p>该测试需要本地 MySQL / Redis / RabbitMQ 全部启动，因此加了 {@code @Disabled}，
 * GitHub Actions 的 CI 流水线不会执行它，避免出现 ApplicationContext 加载失败。</p>
 *
 * <p>本地调试方式：IDEA 里右键单个方法 Run 即可（IDEA 会忽略 @Disabled 手动执行）。</p>
 */
@SpringBootTest
@ActiveProfiles("dev")
@Disabled("仅本地手动执行：需要本地 RabbitMQ/MySQL/Redis，CI 环境不启动该测试")
class MqIntegrationTest {

    @Autowired
    private RabbitMessagePublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Test
    @DisplayName("真实 MQ 收发闭环：发送后能取回同一条消息")
    void testSendAndReceiveMessage() {
        // 先清空队列，避免读到历史遗留消息导致测试误通过
        amqpAdmin.purgeQueue(RabbitTopologyConfig.TEST_QUEUE);

        // 用唯一内容，确保取回的确实是本次发送的消息
        String expectMsg = "rabbitmq ok - " + UUID.randomUUID();

        publisher.sendTestMessage(expectMsg);

        Object actual = rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.TEST_QUEUE, 5000L);
        assertNotNull(actual, "5 秒内没有从 foundation.test.queue 收到消息，检查 RabbitMQ 是否启动");
        assertEquals(expectMsg, String.valueOf(actual));
        System.out.println("✅ MQ 收发闭环通过：" + actual);
    }

    @Test
    @DisplayName("真实 MQ 发送文档解析任务")
    void testSendDocParseTask() {
        DocParseTaskDTO task = new DocParseTaskDTO();
        task.setDocId(1L);
        task.setFileName("测试文档.pdf");
        task.setFileUrl("http://localhost:9000/kaoyan-files/test/test.pdf");

        publisher.sendDocParseTask(task);
        System.out.println("✅ 文档解析任务已投递：" + task.getFileName());
    }
}
