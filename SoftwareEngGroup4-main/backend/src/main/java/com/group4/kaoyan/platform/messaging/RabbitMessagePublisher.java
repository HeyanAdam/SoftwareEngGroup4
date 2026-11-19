package com.group4.kaoyan.platform.messaging;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class RabbitMessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    public RabbitMessagePublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendTestMessage(String message) {
        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.EXCHANGE,
                RabbitTopologyConfig.TEST_ROUTING_KEY,
                message
        );
    }

    public void sendDocParseTask(Object task) {
        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.EXCHANGE,
                RabbitTopologyConfig.DOC_PARSE_ROUTING_KEY,
                task
        );
    }
}
