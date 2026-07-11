package com.ehtesham.securebank.auth.service.impl;

import com.ehtesham.securebank.auth.dto.ActiveSessionResponse;
import com.ehtesham.securebank.auth.entity.RefreshToken;
import com.ehtesham.securebank.auth.repository.RefreshTokenRepository;
import com.ehtesham.securebank.auth.service.RefreshTokenService;
import com.ehtesham.securebank.common.exception.TokenExpiredException;
import com.ehtesham.securebank.common.exception.TokenReuseDetectedException;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-expiration}")
    private long refreshExpiration;
    // self-injection — Spring resolves this to the PROXIED bean,
    // not "this" directly, because it's a constructor-injected
    // dependency, not a direct method call
    private final RefreshTokenService self;
    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            @org.springframework.context.annotation.Lazy RefreshTokenService self
            ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.self = self;
    }

    @Override
    @Transactional
    public RefreshToken createRefreshToken(User user) {

        // NOTE: removed the old "revoke ALL previous tokens" call
        // here — that was the ONE-DEVICE-ONLY restriction. We're
        // deliberately NOT revoking other families on a fresh
        // login anymore, since each login should start its OWN
        // independent family (this directly sets up 0.5f, multi-device,
        // without fully building it yet — login itself no longer
        // assumes "only one session ever")

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setTokenFamily(UUID.randomUUID().toString());  // NEW family
        refreshToken.setUser(user);
        refreshToken.setExpiryDate(
                Instant.now().plusMillis(refreshExpiration));
        refreshToken.setRevoked(false);

        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public RefreshToken rotateToken(String oldTokenValue) {

        RefreshToken oldToken = refreshTokenRepository
                .findByToken(oldTokenValue)
                .orElseThrow(() ->
                        new TokenExpiredException("Invalid refresh token"));

        if (oldToken.isRevoked()) {

            // calling through "self" — THIS goes through Spring's
            // proxy correctly, REQUIRES_NEW actually takes effect
            self.revokeTokenFamily(oldToken.getTokenFamily());

            throw new TokenReuseDetectedException(
                    "This session has been terminated due to " +
                            "suspicious activity. Please log in again.");
        }

        if (oldToken.getExpiryDate().isBefore(Instant.now())) {
            throw new TokenExpiredException("Refresh token has expired");
        }

        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        RefreshToken newToken = new RefreshToken();
        newToken.setToken(UUID.randomUUID().toString());
        newToken.setTokenFamily(oldToken.getTokenFamily());
        newToken.setUser(oldToken.getUser());
        newToken.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        newToken.setRevoked(false);

        return refreshTokenRepository.save(newToken);
    }

    @Override
    public RefreshToken verifyRefreshToken(String token) {

        RefreshToken refreshToken = refreshTokenRepository
                .findByToken(token)
                .orElseThrow(() ->
                        new TokenExpiredException("Invalid refresh token"));

        if (refreshToken.isRevoked()) {
            throw new TokenExpiredException("Refresh token has been revoked");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            throw new TokenExpiredException("Refresh token has expired");
        }

        return refreshToken;
    }

    @Override
    @Transactional
    public void revokeAllUserTokens(User user) {
        refreshTokenRepository.revokeAllUserTokens(user);
    }

    @Override
    @Transactional
    public void revokeByToken(String token) {
        refreshTokenRepository
                .findByToken(token)
                .ifPresent(refreshToken ->
                        refreshTokenRepository
                                .revokeAllUserTokens(refreshToken.getUser()));
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void revokeTokenFamily(String tokenFamily) {
        refreshTokenRepository.revokeByTokenFamily(tokenFamily);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ActiveSessionResponse> getActiveSessions(
            User user, String currentTokenFamily) {

        List<Object[]> results = refreshTokenRepository
                .findActiveSessionSummariesByUser(user);

        return results.stream()
                .map(row -> ActiveSessionResponse.builder()
                        .tokenFamily((String) row[0])
                        .createdAt((LocalDateTime) row[1])
                        .expiresAt(
                                ((Instant) row[2])
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toLocalDateTime())
                        .currentSession(
                                row[0].equals(currentTokenFamily))
                        .build())
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    @Transactional
    public void revokeSession(User user, String tokenFamily) {

        // ownership check — make sure this family actually belongs
        // to THIS user, before letting them revoke it. Without this,
        // a malicious user could pass ANY family ID and kill ANOTHER
        // user's session
        boolean belongsToUser = refreshTokenRepository
                .existsByUserAndTokenFamily(user, tokenFamily);

        if (!belongsToUser) {
            throw new com.ehtesham.securebank.common.exception
                    .ResourceNotFoundException("Session not found");
        }

        refreshTokenRepository.revokeByTokenFamily(tokenFamily);
    }
}