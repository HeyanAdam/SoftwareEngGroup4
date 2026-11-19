package com.group4.kaoyan.platform.messaging;

import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
public class DocParseConsumer {
    private static final Logger log = LoggerFactory.getLogger(DocParseConsumer.class);

    @RabbitListener(queues = RabbitTopologyConfig.DOC_PARSE_QUEUE)
    public void consume(DocParseTaskDTO task, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            log.info("收到文档解析任务：docId={}, fileName={}", task.getDocId(), task.getFileName());

            // 后面写业务：MinIO下载、切片、Embedding、Milvus入库

            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("文档解析任务失败", e);
            channel.basicNack(tag, false, true);
        }
    }
}
