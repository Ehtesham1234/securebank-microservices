package com.ehtesham.securebank.notification.publisher;

import com.ehtesham.securebank.notification.dto.NotificationEvent;
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

    private final KafkaTemplate<String, NotificationEvent>
            notificationKafkaTemplate;

    public NotificationEventPublisher(
            KafkaTemplate<String, NotificationEvent>
                    notificationKafkaTemplate) {
        this.notificationKafkaTemplate = notificationKafkaTemplate;
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
                        + "\n\nThis OTP is valid for 10 minutes."
                        + "\n\nIf you did not request this, "
                        + "please ignore this email."
                        + "\n\nSecureBank Security Team")
                .build());
    }

    @Async
    public void publishKycSubmission(String email) {
        publish(NotificationEvent.builder()
                .eventType("KYC_SUBMITTED")
                .recipientEmail(email)
                .subject("SecureBank - KYC Submission Received")
                .body("Your KYC documents have been received "
                        + "and are under review.\n\n"
                        + "We will notify you once verification "
                        + "is complete.\n\nSecureBank Team")
                .build());
    }

    @Async
    public void publishKycVerified(String email) {
        publish(NotificationEvent.builder()
                .eventType("KYC_VERIFIED")
                .recipientEmail(email)
                .subject("SecureBank - KYC Verified Successfully")
                .body("Congratulations! Your KYC has been verified.\n\n"
                        + "Your SAVINGS account has been created.\n"
                        + "You can now access all banking features."
                        + "\n\nSecureBank Team")
                .build());
    }

    @Async
    public void publishKycRejected(String email, String reason) {
        publish(NotificationEvent.builder()
                .eventType("KYC_REJECTED")
                .recipientEmail(email)
                .subject("SecureBank - KYC Verification Failed")
                .body("Your KYC verification was unsuccessful.\n\n"
                        + "Reason: " + reason + "\n\n"
                        + "Please resubmit with correct documents."
                        + "\n\nSecureBank Team")
                .build());
    }

    @Async
    public void publishTransactionAlert(String email,
                                        String transactionRef, String type,
                                        String amount, String balanceAfter) {
        publish(NotificationEvent.builder()
                .eventType("TRANSACTION_ALERT")
                .recipientEmail(email)
                .subject("SecureBank - " + type + " Alert")
                .body("A " + type.toLowerCase()
                        + " of ₹" + amount
                        + " was processed on your account.\n\n"
                        + "Reference: " + transactionRef
                        + "\nBalance after transaction: ₹"
                        + balanceAfter
                        + "\n\nIf this wasn't you, contact us immediately."
                        + "\n\nSecureBank Security Team")
                .build());
    }

    private void publish(NotificationEvent event) {
        try {
            notificationKafkaTemplate.send(
                    TOPIC,
                    event.getRecipientEmail(),
                    event);
            log.info("Notification published: type={}, to={}",
                    event.getEventType(),
                    event.getRecipientEmail());
        } catch (Exception e) {
            log.error("Failed to publish notification: " +
                            "type={}, to={}, error={}",
                    event.getEventType(),
                    event.getRecipientEmail(),
                    e.getMessage());
        }
    }
}