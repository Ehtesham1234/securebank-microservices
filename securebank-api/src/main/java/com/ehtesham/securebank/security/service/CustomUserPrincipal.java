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
    // Bug fix: carries the access token's tokenFamily claim (if present)
    // so AccountSecurityController.getActiveSessions() can identify
    // which listed session is "this one" — previously there was no
    // wiring for this at all.
    private final String tokenFamily;

    public CustomUserPrincipal(
            Long userId,
            String email,
            String password,
            UserStatus userStatus,
            LocalDateTime lockedUntil,
            boolean emailVerified,
            Collection<? extends GrantedAuthority> authorities) {
        this(userId, email, password, userStatus, lockedUntil,
                emailVerified, authorities, null);
    }

    public CustomUserPrincipal(
            Long userId,
            String email,
            String password,
            UserStatus userStatus,
            LocalDateTime lockedUntil,
            boolean emailVerified,
            Collection<? extends GrantedAuthority> authorities,
            String tokenFamily) {
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
        this.tokenFamily = tokenFamily;
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