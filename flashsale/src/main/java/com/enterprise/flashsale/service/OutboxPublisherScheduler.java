package com.enterprise.flashsale.service;

import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.entity.TransactionalOutbox;
import com.enterprise.flashsale.repository.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OutboxPublisherScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherScheduler.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    OutboxPublisherScheduler(OutboxRepository outboxRepository, KafkaTemplate<String, Object> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 1000) // Runs every 1 second
    @Transactional
    public void publishOutboxEvents() {
        List<TransactionalOutbox> unprocessedEvents = outboxRepository.findTop50ByProcessedFalseOrderByCreatedAtAsc();

        if (unprocessedEvents.isEmpty()) {
            return;
        }

        log.info("Found {} unprocessed outbox events. Shipping to Kafka...", unprocessedEvents.size());

        for (TransactionalOutbox event : unprocessedEvents) {
            kafkaTemplate.send(KafkaConfig.ORDER_FINALIZED_TOPIC, event.getAggregateId(), event.getPayload())
                    .whenComplete((result, ex) -> {
                        if (ex == null) {
                            event.setProcessed(true);
                            outboxRepository.save(event);
                            log.info("Outbox event {} published to Kafka successfully.", event.getEventId());
                        } else {
                            log.error("Failed to publish outbox event {}: {}", event.getEventId(), ex.getMessage());
                        }
                    });
        }
    }
}