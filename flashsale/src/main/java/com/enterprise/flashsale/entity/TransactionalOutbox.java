package com.enterprise.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transactional_outbox", indexes = {
        // CRITICAL: Required for OutboxPublisherScheduler performance
        @Index(name = "idx_outbox_processed_created", columnList = "processed, createdAt")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TransactionalOutbox {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String eventType;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}