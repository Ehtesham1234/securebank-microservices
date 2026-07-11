package com.ehtesham.securebank.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class NotificationEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(NotificationEventPublisher.class);

    private static final String TOPIC = "notification-events";

    private final KafkaTemplate<String, NotificationEvent> kafkaTemplate;

    public NotificationEventPublisher(
            KafkaTemplate<String, NotificationEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Async
    public void publishOtpEmail(String email, String otp,
                                String purposeLabel) {
        publish(NotificationEvent.builder()
                .eventType("OTP")
                .recipientEmail(email)
                .subject("SecureBank - " + purposeLabel + " OTP")
                .body("Your OTP for "
                        + purposeLabel.toLowerCase()
                        + " is: " + otp
                        + "\n\nValid for 10 minutes.\n\nSecureBank Team")
                .build());
    }

    @Async
    public void publishKycVerified(String email) {
        publish(NotificationEvent.builder()
                .eventType("KYC_VERIFIED")
                .recipientEmail(email)
                .subject("SecureBank - KYC Verified")
                .body("Your KYC has been verified. "
                        + "Your SAVINGS account is now active.\n\n"
                        + "SecureBank Team")
                .build());
    }

    @Async
    public void publishKycRejected(String email, String reason) {
        publish(NotificationEvent.builder()
                .eventType("KYC_REJECTED")
                .recipientEmail(email)
                .subject("SecureBank - KYC Verification Failed")
                .body("Your KYC was unsuccessful.\n\nReason: "
                        + reason
                        + "\n\nPlease resubmit.\n\nSecureBank Team")
                .build());
    }
    @Async
    public void publishKycSubmission(String email) {
        publish(NotificationEvent.builder()
                .eventType("KYC_SUBMISSION")
                .recipientEmail(email)
                .subject("SecureBank - KYC Submission Received")
                .body("Your KYC documents have been received and are under review.\n\n"
                        + "We will notify you once the verification is complete.\n\n"
                        + "SecureBank Team")
                .build());
    }


    private void publish(NotificationEvent event) {
        try {
            kafkaTemplate.send(TOPIC,
                    event.getRecipientEmail(), event);
            log.info("Published: type={}, to={}",
                    event.getEventType(), event.getRecipientEmail());
        } catch (Exception e) {
            log.error("Failed to publish notification: {}",
                    e.getMessage());
        }
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NotificationEvent {
        private String eventType;
        private String recipientEmail;
        private String subject;
        private String body;
    }
}