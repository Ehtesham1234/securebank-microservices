package com.ehtesham.account_service.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/**
 * M5 fix: minimal shape of securebank-api's internal user lookup — only
 * the field this service actually needs (current userStatus).
 *
 * securebank-api's actual InternalUserResponse also includes
 * firstName/lastName/email/role — ignoreUnknown so deserializing the
 * real response doesn't fail on those. Without this, EVERY call here
 * failed to deserialize, which silently short-circuited straight to
 * UserStatusClientFallback (fail-open) on every single invocation — the
 * live-status check was never actually running, just always falling
 * through to "couldn't verify, proceed anyway."
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter
@Setter
public class InternalUserStatusResponse {
    private Long id;
    private String userStatus;
}
