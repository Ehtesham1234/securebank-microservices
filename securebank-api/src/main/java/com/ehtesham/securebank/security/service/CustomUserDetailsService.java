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
}