package com.enterprise.flashsale.consumer;

import com.enterprise.flashsale.config.RabbitMQConfig;
import com.enterprise.flashsale.service.InventoryReservationService;
import com.enterprise.flashsale.service.InventoryReservationService.ReservationTimeoutEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryTimeoutConsumer {

    private final InventoryReservationService reservationService;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.RESERVATION_TIMEOUT_QUEUE)
    public void handleReservationTimeout(String messagePayload) {
        try {
            ReservationTimeoutEvent event = objectMapper.readValue(messagePayload, ReservationTimeoutEvent.class);
            log.info("Received timeout event for reservation: {}", event.getReservationId());
            reservationService.processTimeout(event.getReservationId(), event.getProductId());
        } catch (Exception e) {
            log.error("Failed to process reservation timeout for payload: {}", messagePayload, e);
            throw new RuntimeException("Timeout processing failed", e);
        }
    }
}