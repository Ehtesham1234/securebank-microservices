package com.ehtesham.securebank.user.repository;

import com.ehtesham.securebank.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
// Add to UserRepository interface:

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 " +
            "WHERE u.email = :email")
    void incrementFailedAttempts(String email);

    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null " +
            "WHERE u.email = :email")
    void resetFailedAttempts(String email);

    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :lockedUntil " +
            "WHERE u.email = :email")
    void lockAccount(String email, LocalDateTime lockedUntil);
    @Query("SELECT u FROM User u WHERE " +
            "(:search IS NULL OR :search = '' " +
            "OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) " +
            "OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);
}