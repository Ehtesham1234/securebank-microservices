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
public class LoanEventConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(LoanEventConsumer.class);

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    public LoanEventConsumer(EmailService emailService,
                             ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }
    @KafkaListener(
            topics = "loan-events",
            groupId = "notification-loan-group",
            containerFactory = "stringKafkaListenerContainerFactory")
    public void handleLoanEvent(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.OFFSET) long offset) {

        try {
            JsonNode node = objectMapper.readTree(payload);
            String eventType = node.get("eventType").asText();
            String email = node.get("email").asText();

            log.info("Received loan event: type={}, to={}, offset={}",
                    eventType, email, offset);

            switch (eventType) {
                case "LOAN_DISBURSED" -> {
                    String amount = node.get("amount").asText();
                    String ref = node.get("transactionRef").asText();
                    emailService.sendEmail(email,
                            "SecureBank - Loan Disbursed Successfully",
                            "Your loan has been approved and ₹"
                                    + amount + " has been credited to your account.\n\n"
                                    + "Reference: " + ref
                                    + "\n\nSecureBank Team");
                }
                case "LOAN_FAILED" -> {
                    String reason = node.get("reason").asText();
                    emailService.sendEmail(email,
                            "SecureBank - Loan Disbursement Failed",
                            "We were unable to process your loan disbursement.\n\n"
                                    + "Reason: " + reason
                                    + "\n\nPlease contact support.\n\nSecureBank Team");
                }
                default -> log.warn("Unknown loan event type: {}",
                        eventType);
            }

        } catch (Exception e) {
            log.error("Failed to process loan event: {}",
                    e.getMessage());
        }
    }
}