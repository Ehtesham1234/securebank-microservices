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

        user.setUserStatus(UserStatus.ACTIVE);
        userRepository.save(user);

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