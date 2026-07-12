package com.ehtesham.securebank.notification.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Async
    public void sendOtpEmail(String toEmail, String otp, String purposeLabel) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("SecureBank - " + purposeLabel + " OTP");
        message.setText(
                "Your OTP for " + purposeLabel.toLowerCase() + " is: " + otp + "\n\n" +
                        "This OTP is valid for 10 minutes.\n\n" +
                        "If you did not request this, please ignore this email.\n\n" +
                        "SecureBank Security Team"
        );
        mailSender.send(message);
    }

    @Async
    public void sendKycSubmissionNotification(String customerEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(customerEmail);
        message.setSubject("SecureBank - KYC Submission Received");
        message.setText(
                "Your KYC documents have been received and are under review.\n\n" +
                        "We will notify you once the verification is complete.\n\n" +
                        "SecureBank Team"
        );
        mailSender.send(message);
    }

    @Async
    public void sendKycVerifiedNotification(String customerEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(customerEmail);
        message.setSubject("SecureBank - KYC Verified Successfully");
        message.setText(
                "Congratulations! Your KYC has been verified.\n\n" +
                        "Your SAVINGS account has been created.\n" +
                        "You can now access all banking features.\n\n" +
                        "SecureBank Team"
        );
        mailSender.send(message);
    }

    @Async
    public void sendKycRejectedNotification(
            String customerEmail, String reason) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(customerEmail);
        message.setSubject("SecureBank - KYC Verification Failed");
        message.setText(
                "Your KYC verification was unsuccessful.\n\n" +
                        "Reason: " + reason + "\n\n" +
                        "Please resubmit with correct documents.\n\n" +
                        "SecureBank Team"
        );
        mailSender.send(message);
    }
}