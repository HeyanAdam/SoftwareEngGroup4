package com.group4.kaoyan.platform.messaging;

import com.group4.kaoyan.dto.DocParseTaskDTO;
import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import com.group4.kaoyan.platform.messaging.service.ConsumeLogService;
import com.group4.kaoyan.platform.messaging.service.DocParseTaskHandler;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 文档解析消费者。
 *
 * <p>消费幂等由 sys_message_consume_log 保证；失败最多重试 3 次，
 * 超过后 reject 进死信队列，同时把异步任务置 FAILED，不再无限 requeue。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocParseConsumer {

    private static final String CONSUMER_NAME = "doc-parse-consumer";
    private static final int MAX_RETRY = 3;

    private final ConsumeLogService consumeLogService;
    private final DocParseTaskHandler docParseTaskHandler;
    private final AsyncTaskService asyncTaskService;

    @RabbitListener(queues = RabbitTopologyConfig.DOC_PARSE_QUEUE)
    public void consume(DocParseTaskDTO task, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long docId = task == null ? null : task.getDocId();
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            // 没有 messageId 时退化为按文档去重，保证重复投递不会重复处理
            messageId = "doc-" + docId;
        }
        String bizKey = "DOC:" + docId;

        ConsumeLogService.Begin begin = consumeLogService.begin(messageId, CONSUMER_NAME, bizKey);
        if (begin.decision() == ConsumeLogService.Decision.DONE) {
            log.info("消息已处理过，直接确认 messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (begin.decision() == ConsumeLogService.Decision.IN_FLIGHT) {
            // 另一个实例正在处理，避免重复执行
            log.warn("消息正在被其他实例处理，跳过 messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            docParseTaskHandler.handle(task);
            consumeLogService.succeed(begin.logId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            String reason = brief(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.error("文档解析任务失败 docId={} messageId={}：{}", docId, messageId, reason, e);
            consumeLogService.fail(begin.logId(), reason);
            int retry = begin.retryCount() + 1;
            if (retry < MAX_RETRY) {
                // 任务按退避记录下一次重试；消息立即 requeue，由下一轮消费重跑
                asyncTaskService.retryOrFailByDoc(docId, reason);
                channel.basicNack(deliveryTag, false, true);
            } else {
                asyncTaskService.failByDoc(docId, reason);
                consumeLogService.deadLetter(begin.logId(), reason);
                // requeue=false：进入 kaoyan.doc.parse.dlq
                channel.basicReject(deliveryTag, false);
            }
        }
    }

    private static String brief(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
