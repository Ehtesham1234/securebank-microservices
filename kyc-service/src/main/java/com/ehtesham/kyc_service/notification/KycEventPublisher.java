package com.ehtesham.kyc_service.notification;

import com.ehtesham.kyc_service.outbox.OutboxEvent;
import com.ehtesham.kyc_service.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Bug fix: this used to be a bare @Async fire-and-forget
 * kafkaTemplate.send() with no persistence at all — if the send failed,
 * or the JVM went down between the async task being scheduled and
 * actually running, the notification was lost outright with nothing to
 * retry and no trace it ever should have gone out. Now writes to the
 * outbox table in the SAME transaction as the KYC decision that
 * triggered it (verifyKyc/rejectKyc/submitKyc are all @Transactional),
 * so the event can never be lost independently of that decision —
 * OutboxPublisher delivers it afterward, same pattern account-service
 * and loan-service already use.
 */
@Component
public class KycEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(KycEventPublisher.class);

    private static final String TOPIC = "kyc-events";

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public KycEventPublisher(
            OutboxRepository outboxRepository,
            ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    public void publishKycSubmitted(Long userId,
                                    String userEmail) {
        publish(userId, "KYC_SUBMITTED", userEmail,
                "Your KYC documents have been received " +
                        "and are under review.", null);
    }

    public void publishKycVerified(Long userId,
                                   String userEmail) {
        publish(userId, "KYC_VERIFIED", userEmail,
                "Congratulations! Your KYC has been verified. " +
                        "Your savings account is now active.", null);
    }

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

            OutboxEvent outboxEvent = new OutboxEvent();
            outboxEvent.setTopic(TOPIC);
            outboxEvent.setAggregateId(userId.toString());
            outboxEvent.setEventType(eventType);
            outboxEvent.setPayload(payload);
            outboxRepository.save(outboxEvent);

            log.info("Queued KYC event for outbox delivery: " +
                    "type={}, userId={}", eventType, userId);

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize KYC event: {}",
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