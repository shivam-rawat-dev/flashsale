package com.enterprise.flashsale.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Entity
@AllArgsConstructor
@Table(name = "transactional_outbox")
@NoArgsConstructor
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

    private boolean processed = false;

    private Instant createdAt = Instant.now();


}