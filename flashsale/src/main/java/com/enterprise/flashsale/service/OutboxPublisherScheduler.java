package com.enterprise.flashsale.service;

import com.enterprise.flashsale.config.RabbitMQConfig;
import com.enterprise.flashsale.entity.TransactionalOutbox;
import com.enterprise.flashsale.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxPublisherScheduler {

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<TransactionalOutbox> pendingEvents = outboxRepository.findByProcessedFalseOrderByCreatedAtAsc(PageRequest.of(0, 50));

        for (TransactionalOutbox outbox : pendingEvents) {
            try {
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.FLASH_SALE_EXCHANGE,
                        RabbitMQConfig.ORDER_ROUTING_KEY,
                        outbox.getPayload()
                );
                outbox.setProcessed(true);
                outboxRepository.save(outbox);
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", outbox.getEventId(), e);
                break; // Stop batch on failure to prevent continuous unhandled exceptions
            }
        }
    }
}