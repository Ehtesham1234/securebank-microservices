package com.ehtesham.kyc_service.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Bug fix: kyc-service used to publish KYC_SUBMITTED/VERIFIED/REJECTED
 * notifications via a bare @Async fire-and-forget kafkaTemplate.send()
 * with no persistence at all — a failed send (or a JVM restart between
 * the async task being scheduled and actually running) lost the
 * notification outright, with zero trace and no retry. Mirrors the
 * outbox pattern account-service and loan-service already use: the
 * event is persisted in the SAME transaction as the KYC decision, so it
 * can never be lost even if Kafka is briefly unavailable — a separate
 * scheduled publisher (see OutboxPublisher) delivers it afterward.
 */
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
    private String aggregateId;      // userId — Kafka message key

    @Column(name = "event_type", nullable = false)
    private String eventType;        // "KYC_SUBMITTED", "KYC_VERIFIED", "KYC_REJECTED"

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
