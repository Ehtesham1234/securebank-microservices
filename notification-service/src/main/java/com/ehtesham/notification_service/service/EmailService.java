package com.ehtesham.notification_service.service;

public interface EmailService {
    public void sendEmail(String to, String subject, String body);
}
