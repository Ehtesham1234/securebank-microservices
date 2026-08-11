package com.ehtesham.kyc_service.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

    @Scheduled(fixedDelay = 5000)   // runs every 5 seconds
    @Transactional
    public void publishPendingEvents() {

        List<OutboxEvent> pending = outboxRepository
                .findByPublishedFalseOrderByCreatedAtAsc();

        if (pending.isEmpty()) return;

        log.info("Publishing {} pending KYC outbox events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                // Blocks on the send future (bounded timeout) so a
                // broker-side failure throws and is caught below instead
                // of being silently recorded as delivered — see
                // account-service/loan-service's OutboxPublisher for the
                // same fix and full reasoning.
                kafkaTemplate.send(
                                event.getTopic(),
                                event.getAggregateId(),
                                event.getPayload())
                        .get(5, TimeUnit.SECONDS);

                event.setPublished(true);
                event.setPublishedAt(LocalDateTime.now());
                outboxRepository.save(event);

                log.info("Published KYC outbox event: type={}, id={}",
                        event.getEventType(), event.getId());

            } catch (Exception e) {
                log.error("Failed to publish KYC outbox event id={}: {}",
                        event.getId(), e.getMessage());
                // Leave as unpublished — will retry on next cycle
            }
        }
    }
}
