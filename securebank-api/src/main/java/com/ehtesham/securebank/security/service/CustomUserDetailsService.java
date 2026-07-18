package com.ehtesham.securebank.security.service;

import com.ehtesham.securebank.common.enums.UserStatus;
import com.ehtesham.securebank.common.exception.AccountClosedException;
import com.ehtesham.securebank.common.exception.AccountLockedException;
import com.ehtesham.securebank.common.exception.AccountSuspendedException;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email)

            throws UsernameNotFoundException {User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));

        return new CustomUserPrincipal(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getUserStatus(),
                user.getLockedUntil(),
                user.isEmailVerified(),
                List.of(new SimpleGrantedAuthority(
                        "ROLE_" + user.getRole().name()
                ))
        );
    }
    /**
     * Builds a CustomUserPrincipal from gateway headers.
     * Used when requests come through the API gateway —
     * no DB hit needed since gateway already validated the JWT.
     */
    public CustomUserPrincipal buildPrincipalFromHeaders(
            Long userId, String email,
            String role, String userStatus) {

        UserStatus status = UserStatus.ACTIVE;
        try {
            if (userStatus != null && !userStatus.isBlank()) {
                status = UserStatus.valueOf(userStatus);
            }
        } catch (IllegalArgumentException e) {
            // Unknown status — default to ACTIVE, let
            // downstream filters handle edge cases
        }

        return new CustomUserPrincipal(
                userId,
                email != null ? email : "",
                // No password needed — already authenticated
                // by gateway. Empty string is safe here.
                "",
                status,
                // No lock info from headers — not locked
                null,
                // Email already verified (they're logged in)
                true,
                List.of(new SimpleGrantedAuthority(
                        role != null ? role : "")));
    }
}