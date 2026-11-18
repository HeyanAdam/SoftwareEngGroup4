package com.group4.kaoyan;

import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.group4.kaoyan.platform.messaging.RabbitMessagePublisher;
import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("dev")
public class MqTest {

    @Autowired
    private RabbitMessagePublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * 收发闭环测试：
     * 1. 通过 Publisher 往 foundation.test.queue 发一条消息
     * 2. 用 receiveAndConvert 同步取回
     * 3. 断言取回内容与发送内容一致
     */
    @Test
    void testSendAndReceiveMessage() {
        String expectMsg = "rabbitmq ok";

        publisher.sendTestMessage(expectMsg);

        Object actual = rabbitTemplate.receiveAndConvert(RabbitTopologyConfig.TEST_QUEUE, 3000L);
        assertNotNull(actual, "3 秒内没有从 foundation.test.queue 收到消息，检查 RabbitMQ 是否启动");
        assertEquals(expectMsg, String.valueOf(actual));
        System.out.println("✅ MQ 收发闭环通过：" + actual);
    }

    /**
     * 文档解析任务发送测试：发一条真实的 DocParseTaskDTO 到 doc_parse_queue，
     * 由 DocParseConsumer 监听消费（消费日志会打印任务信息）。
     */
    @Test
    void testSendDocParseTask() {
        DocParseTaskDTO task = new DocParseTaskDTO();
        task.setDocId(1L);
        task.setFileName("测试文档.pdf");
        task.setFileUrl("http://localhost:9000/kaoyan-files/test/test.pdf");

        publisher.sendDocParseTask(task);
        System.out.println("✅ 文档解析任务已投递：" + task.getFileName());
    }
}
