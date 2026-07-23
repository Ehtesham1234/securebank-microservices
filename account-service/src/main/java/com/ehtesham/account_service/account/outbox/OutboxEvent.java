package com.ehtesham.account_service.account.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String topic;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;      // e.g. loanId — Kafka message key

    @Column(name = "event_type", nullable = false)
    private String eventType;        // "ACCOUNT_CREDITED", "ACCOUNT_CREDIT_FAILED"

    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;          // JSON of the event

    @Column(nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
