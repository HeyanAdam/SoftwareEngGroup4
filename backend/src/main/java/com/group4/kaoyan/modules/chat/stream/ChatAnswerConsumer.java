package com.group4.kaoyan.modules.chat.stream;

import com.group4.kaoyan.dto.ChatAnswerTaskDTO;
import com.group4.kaoyan.platform.messaging.RabbitTopologyConfig;
import com.group4.kaoyan.platform.messaging.service.AsyncTaskService;
import com.group4.kaoyan.platform.messaging.service.ConsumeLogService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * AI 回答消费者（A-2）。
 *
 * <p>幂等：{@code sys_message_consume_log(messageId, consumer)} 保证重复投递只执行一次；
 * 失败：前 3 次投回延迟重试队列（30 秒后重新入队），超限 reject 进聊天死信队列，
 * 同时把 {@code sys_async_task} 置 FAILED。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatAnswerConsumer {

    private static final String CONSUMER_NAME = "chat-answer-consumer";
    private static final int MAX_RETRY = 3;

    private final ConsumeLogService consumeLogService;
    private final AsyncTaskService asyncTaskService;
    private final ChatAnswerTaskHandler taskHandler;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitTopologyConfig.CHAT_ANSWER_QUEUE)
    public void consume(ChatAnswerTaskDTO task, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        Long userMessageId = task == null ? null : task.getUserMessageId();
        String messageId = message.getMessageProperties().getMessageId();
        if (messageId == null || messageId.isBlank()) {
            messageId = "chat-" + userMessageId + "-" + (task == null ? "" : task.getStreamId());
        }
        String bizKey = "CHAT:" + userMessageId;

        ConsumeLogService.Begin begin = consumeLogService.begin(messageId, CONSUMER_NAME, bizKey);
        if (begin.decision() == ConsumeLogService.Decision.DONE) {
            log.info("聊天消息已处理过，直接确认 messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (begin.decision() == ConsumeLogService.Decision.IN_FLIGHT) {
            log.warn("聊天消息正在被其他实例处理，跳过 messageId={}", messageId);
            channel.basicAck(deliveryTag, false);
            return;
        }

        Long taskId = asyncTaskService.markRunningByChat(userMessageId);
        if (taskId == null) {
            // 任务已完成 / 已取消 / 其他实例已接手，不再重复生成
            consumeLogService.succeed(begin.logId());
            channel.basicAck(deliveryTag, false);
            return;
        }

        try {
            taskHandler.handle(task, taskId);
            consumeLogService.succeed(begin.logId());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            String reason = brief(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            log.error("AI 回答任务执行失败 streamId={} messageId={}：{}",
                    task == null ? null : task.getStreamId(), messageId, reason, e);
            consumeLogService.fail(begin.logId(), reason);
            int retry = begin.retryCount() + 1;
            // 当前是第 retry 次尝试：初次 + MAX_RETRY 次重投后才进死信
            if (retry <= MAX_RETRY) {
                asyncTaskService.retryOrFailByChat(userMessageId, reason);
                try {
                    requeueWithDelay(task, messageId);
                    channel.basicAck(deliveryTag, false);
                } catch (Exception publishError) {
                    log.warn("投递延迟重试队列失败，退回立即 requeue：{}", publishError.getMessage());
                    channel.basicNack(deliveryTag, false, true);
                }
            } else {
                asyncTaskService.failByChat(userMessageId, reason);
                consumeLogService.deadLetter(begin.logId(), reason);
                // requeue=false：按队列参数进入 kaoyan.chat.answer.dlq
                channel.basicReject(deliveryTag, false);
            }
        }
    }

    private void requeueWithDelay(ChatAnswerTaskDTO task, String messageId) {
        rabbitTemplate.convertAndSend(RabbitTopologyConfig.DLX_EXCHANGE,
                RabbitTopologyConfig.CHAT_RETRY_ROUTING_KEY, task,
                message -> {
                    message.getMessageProperties().setMessageId(messageId);
                    message.getMessageProperties().setContentType("application/json");
                    return message;
                });
    }

    private static String brief(String text) {
        return text.length() <= 1000 ? text : text.substring(0, 1000);
    }
}
