package com.ehtesham.securebank.user.controller;

import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.common.exception.ResourceNotFoundException;
import com.ehtesham.securebank.user.dto.InternalUserResponse;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal endpoints — called by other microservices only.
 * NOT exposed through the API gateway.
 * Protected at network level (internal Docker network).
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
     * Called by kyc-service after KYC verification.
     * Activates the user — they can now access banking services.
     */
    @PutMapping("/{userId}/activate")
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
     * Called by kyc-service and loan-service to get
     * basic user info without going through the gateway.
     */
    @GetMapping("/{userId}")
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