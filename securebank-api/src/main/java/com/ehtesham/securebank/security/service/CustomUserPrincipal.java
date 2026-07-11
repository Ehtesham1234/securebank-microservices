package com.ehtesham.securebank.security.service;

import com.ehtesham.securebank.common.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.time.LocalDateTime;
import java.util.Collection;

@Getter
public class CustomUserPrincipal extends User {

    private final Long userId;
    private final UserStatus userStatus;
    private final boolean accountNonLocked;
    private final boolean enabled;
    public CustomUserPrincipal(
            Long userId,
            String email,
            String password,
            UserStatus userStatus,
            LocalDateTime lockedUntil,
            boolean emailVerified,
            Collection<? extends GrantedAuthority> authorities) {
        super(email, password, authorities);
        this.userId = userId;
        this.userStatus = userStatus;
        // compute ONCE, at construction time
        this.accountNonLocked = lockedUntil == null
                || lockedUntil.isBefore(LocalDateTime.now());
        // SUSPENDED and CLOSED both mean "not enabled" —
        // computed ONCE, at construction, same pattern as lockout
        this.enabled = emailVerified
                && userStatus != UserStatus.SUSPENDED
                && userStatus != UserStatus.CLOSED;
    }
    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}