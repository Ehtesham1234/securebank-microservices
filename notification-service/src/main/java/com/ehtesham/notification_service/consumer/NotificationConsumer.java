package com.ehtesham.notification_service.consumer;

import com.ehtesham.notification_service.dto.NotificationEvent;
import com.ehtesham.notification_service.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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
            groupId = "notification-service-group")
    public void handleNotification(NotificationEvent event) {
        log.info("Received event: type={}, to={}",
                event.getEventType(), event.getRecipientEmail());

        emailService.sendEmail(
                event.getRecipientEmail(),
                event.getSubject(),
                event.getBody());
    }
}
