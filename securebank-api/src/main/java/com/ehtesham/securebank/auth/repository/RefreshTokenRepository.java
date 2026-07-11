package com.ehtesham.securebank.auth.repository;

import com.ehtesham.securebank.auth.entity.RefreshToken;
import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    // used during logout — revoke all tokens for a user
    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user")
    void revokeAllUserTokens(User user);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true " +
            "WHERE rt.tokenFamily = :tokenFamily")
    void revokeByTokenFamily(String tokenFamily);

    @Query("SELECT DISTINCT rt.tokenFamily, MIN(rt.createdAt), MAX(rt.expiryDate) " +
            "FROM RefreshToken rt " +
            "WHERE rt.user = :user AND rt.revoked = false " +
            "GROUP BY rt.tokenFamily")
    List<Object[]> findActiveSessionSummariesByUser(User user);

    boolean existsByUserAndTokenFamily(User user, String tokenFamily);
    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.revoked = true " +
            "OR rt.expiryDate < :now")
    int deleteExpiredAndRevoked(@Param("now") Instant now);
}