package com.enterprise.flashsale.service;

import com.enterprise.flashsale.config.KafkaConfig;
import com.enterprise.flashsale.entity.TransactionalOutbox;
import com.enterprise.flashsale.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<TransactionalOutbox> pendingEvents = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc(PageRequest.of(0, 50));

        for (TransactionalOutbox outbox : pendingEvents) {
            try {
                kafkaTemplate.send(
                        KafkaConfig.FLASH_SALE_ORDERS_TOPIC,
                        outbox.getAggregateId(),
                        outbox.getPayload()
                );
                outbox.setProcessed(true);
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", outbox.getEventId(), e);
                break;
            }
        }
    }
}
