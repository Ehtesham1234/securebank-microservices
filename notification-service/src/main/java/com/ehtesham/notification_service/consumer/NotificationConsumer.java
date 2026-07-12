package com.ehtesham.notification_service.consumer;
import com.ehtesham.notification_service.dto.NotificationEvent;
import com.ehtesham.notification_service.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class NotificationConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationConsumer.class);

    private final EmailService emailService;

    public NotificationConsumer(EmailService emailService) {
        this.emailService = emailService;
    }

    @KafkaListener(
            topics = "notification-events",
            groupId = "notification-service-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void handleNotification(
            @Payload NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received Kafka event: type={}, to={}, " +
                        "topic={}, partition={}, offset={}",
                event.getEventType(),
                event.getRecipientEmail(),
                topic, partition, offset);

        try {
            emailService.sendEmail(
                    event.getRecipientEmail(),
                    event.getSubject(),
                    event.getBody());

        } catch (Exception e) {
            log.error("Failed to process notification event " +
                            "type={}, to={}: {}",
                    event.getEventType(),
                    event.getRecipientEmail(),
                    e.getMessage());
        }
    }
}
