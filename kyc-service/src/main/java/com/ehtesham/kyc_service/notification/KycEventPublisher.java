package com.ehtesham.kyc_service.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class KycEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(KycEventPublisher.class);

    private static final String TOPIC = "kyc-events";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public KycEventPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Async
    public void publishKycSubmitted(Long userId,
                                    String userEmail) {
        publish(userId, "KYC_SUBMITTED", userEmail,
                "Your KYC documents have been received " +
                        "and are under review.", null);
    }

    @Async
    public void publishKycVerified(Long userId,
                                   String userEmail) {
        publish(userId, "KYC_VERIFIED", userEmail,
                "Congratulations! Your KYC has been verified. " +
                        "Your savings account is now active.", null);
    }

    @Async
    public void publishKycRejected(Long userId,
                                   String userEmail, String reason) {
        publish(userId, "KYC_REJECTED", userEmail,
                "Your KYC verification was unsuccessful.",
                reason);
    }

    private void publish(Long userId, String eventType,
                         String userEmail, String message, String reason) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", eventType);
            event.put("userId", userId);
            event.put("recipientEmail", userEmail);
            event.put("subject", "SecureBank - "
                    + formatEventType(eventType));
            event.put("body", buildBody(
                    eventType, message, reason));

            String payload = objectMapper
                    .writeValueAsString(event);
            kafkaTemplate.send(TOPIC,
                    userId.toString(), payload);

            log.info("Published KYC event: type={}, userId={}",
                    eventType, userId);

        } catch (JsonProcessingException e) {
            log.error("Failed to publish KYC event: {}",
                    e.getMessage());
        }
    }

    private String buildBody(String eventType,
                             String message, String reason) {
        StringBuilder body = new StringBuilder(message);
        body.append("\n\n");
        if (reason != null) {
            body.append("Reason: ").append(reason)
                    .append("\n\n");
        }
        body.append("SecureBank Team");
        return body.toString();
    }

    private String formatEventType(String eventType) {
        return switch (eventType) {
            case "KYC_SUBMITTED" -> "KYC Submission Received";
            case "KYC_VERIFIED" -> "KYC Verified Successfully";
            case "KYC_REJECTED" -> "KYC Verification Failed";
            default -> eventType;
        };
    }
}