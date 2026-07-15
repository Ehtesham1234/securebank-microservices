package com.ehtesham.loan_service.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class OutboxPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxPublisher(
            OutboxRepository outboxRepository,
            KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {

        List<OutboxEvent> pending = outboxRepository
                .findByPublishedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        log.info("Publishing {} pending loan outbox events",
                pending.size());

        for (OutboxEvent event : pending) {
            try {
                kafkaTemplate.send(
                        event.getTopic(),
                        event.getAggregateId(),
                        event.getPayload());

                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);

                log.info("Published loan outbox event: " +
                                "type={}, id={}",
                        event.getEventType(), event.getId());

            } catch (Exception e) {
                log.error("Failed to publish loan outbox " +
                                "event id={}: {}",
                        event.getId(), e.getMessage());
            }
        }
    }
}