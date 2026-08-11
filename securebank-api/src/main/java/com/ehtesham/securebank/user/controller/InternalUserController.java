package com.ehtesham.securebank.user.controller;

import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.common.exception.ResourceNotFoundException;
import com.ehtesham.securebank.user.dto.InternalUserResponse;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoints — called service-to-service by kyc-service (staff
 * actions on a customer) and account-service (a customer checking their
 * own live status, see M5 in the audit). The caller's JWT is forwarded
 * from whatever authenticated request triggered the call and verified
 * the same way as any other request (see JwtAuthenticationFilter) — WHO
 * may call each endpoint is enforced below via @PreAuthorize.
 */
@RestController
@RequestMapping("/api/v1/internal/users")
public class InternalUserController {

    private final UserRepository userRepository;

    public InternalUserController(
            UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Called by kyc-service after KYC verification, on behalf of the
     * teller who verified it — activates a DIFFERENT user than the
     * caller, so restricted to staff roles.
     */
    @PutMapping("/{userId}/activate")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN')")
    public ResponseEntity<Void> activateUser(
            @PathVariable Long userId) {

        var user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));

        // Bug fix: this used to force ACTIVE unconditionally, with no
        // check on the current status — a KYC-verification action could
        // silently reactivate a user who was separately SUSPENDED
        // (fraud/compliance hold) or CLOSED in the meantime. Only
        // PENDING_KYC (the normal path) or an already-ACTIVE user (a
        // harmless retry/duplicate call) may move to ACTIVE here;
        // anything else is left untouched and reported back.
        if (user.getUserStatus() != UserStatus.PENDING_KYC
                && user.getUserStatus() != UserStatus.ACTIVE) {
            throw new com.ehtesham.securebank.common.exception.AccountOperationException(
                    "Cannot activate userId=" + userId +
                            " — current status is " + user.getUserStatus() +
                            ", not PENDING_KYC");
        }

        user.setUserStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        return ResponseEntity.ok().build();
    }

    // Bug fix: compensating action for the KYC-verification saga gap —
    // activateUser() above commits independently of kyc-service's own
    // transaction (separate service, separate DB). If kyc-service then
    // fails to provision the account/debit card and rolls back its own
    // KYC record to PENDING, the remote activation above had already
    // committed, leaving the user ACTIVE with no account and no verified
    // KYC. kyc-service calls this on that failure path to put the user
    // back where they started. Only reverts a user who is currently
    // ACTIVE — if their status has since changed for an unrelated reason
    // (e.g. a teller already suspended them), this leaves that alone
    // rather than clobbering it.
    @PutMapping("/{userId}/revert-to-pending-kyc")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN')")
    public ResponseEntity<Void> revertToPendingKyc(
            @PathVariable Long userId) {

        var user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));

        if (user.getUserStatus() == UserStatus.ACTIVE) {
            user.setUserStatus(UserStatus.PENDING_KYC);
            userRepository.save(user);
        }

        return ResponseEntity.ok().build();
    }

    /**
     * Called by kyc-service (staff looking up a different customer) and
     * by account-service (a customer's own deposit/withdraw/transfer
     * checking THEIR OWN live status — see M5). Allow staff roles, or
     * the caller looking up their own userId.
     */
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN') or #userId == authentication.details")
    public ResponseEntity<InternalUserResponse> getUser(
            @PathVariable Long userId) {

        var user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "User not found: " + userId));

        return ResponseEntity.ok(
                InternalUserResponse.builder()
                        .id(user.getId())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .email(user.getEmail())
                        .role(user.getRole().name())
                        .userStatus(user.getUserStatus().name())
                        .build());
    }
}