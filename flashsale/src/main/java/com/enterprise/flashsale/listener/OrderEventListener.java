package com.enterprise.flashsale.listener;

import com.enterprise.flashsale.config.KafkaConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventListener {

    @KafkaListener(
            topics = KafkaConfig.ORDER_FINALIZED_TOPIC,
            groupId = "flashsale-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleOrderFinalized(Object payload) {
        log.info("RECEIVED KAFKA EVENT -> Order Payload: {}", payload);
    }
}