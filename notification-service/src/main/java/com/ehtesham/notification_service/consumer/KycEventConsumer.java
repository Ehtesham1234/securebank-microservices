package com.ehtesham.notification_service.consumer;

import com.ehtesham.notification_service.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class KycEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(KycEventConsumer.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public KycEventConsumer(EmailService emailService,
                            ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = "kyc-events",
            groupId = "notification-kyc-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void handleKycEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.get("eventType").asText();
            String email = node.get("recipientEmail").asText();
            String subject = node.get("subject").asText();
            String body = node.get("body").asText();

            log.info("Received KYC event: type={}, to={}, " +
                    "offset={}", eventType, email, offset);

            if (email == null || email.isBlank()) {
                log.warn("KYC event has no recipient email, " +
                        "skipping: type={}", eventType);
                return;
            }

            emailService.sendEmail(email, subject, body);

        } catch (Exception e) {
            log.error("Failed to process KYC event: {}",
                    e.getMessage());
        }
    }
}