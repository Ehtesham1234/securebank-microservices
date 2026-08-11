package com.ehtesham.loan_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * Minimal shape of securebank-api's internal user lookup — only the
 * field this service actually needs (current userStatus). ignoreUnknown
 * so deserializing the real response (which also has
 * firstName/lastName/email/role) doesn't fail on the extra fields.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class InternalUserStatusResponse {
    private Long id;
    private String userStatus;
}
