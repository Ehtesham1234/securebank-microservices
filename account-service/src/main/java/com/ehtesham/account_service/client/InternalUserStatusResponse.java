package com.ehtesham.account_service.client;

import lombok.Getter;
import lombok.Setter;

/**
 * M5 fix: minimal shape of securebank-api's internal user lookup — only
 * the field this service actually needs (current userStatus).
 */
@Getter
@Setter
public class InternalUserStatusResponse {
    private Long id;
    private String userStatus;
}
