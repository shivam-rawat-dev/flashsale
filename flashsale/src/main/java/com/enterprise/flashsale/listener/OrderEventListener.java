package com.enterprise.flashsale.listener;

import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.repository.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final InventoryRepository inventoryRepository;

    public OrderEventListener(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @KafkaListener(topics = KafkaConfig.ORDER_FINALIZED_TOPIC, groupId = "flashsale-group")
    @Transactional
    public void handleOrderFinalized(String payload) {
        log.info("RECEIVED KAFKA EVENT -> Order Finalized Payload: {}", payload);

        // Here you can update downstream reporting, analytics, or increment permanent allocated stock in PostgreSQL
    }
}