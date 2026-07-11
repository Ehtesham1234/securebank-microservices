package com.ehtesham.securebank.notification.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {

    private String eventType;
    private String recipientEmail;
    private String subject;
    private String body;

}