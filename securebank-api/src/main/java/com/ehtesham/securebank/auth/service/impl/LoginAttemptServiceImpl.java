package com.ehtesham.securebank.auth.service.impl;

import com.ehtesham.securebank.auth.service.LoginAttemptService;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    private final UserRepository userRepository;

    public LoginAttemptServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedAttempt(String email) {

        userRepository.incrementFailedAttempts(email);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null
                && user.getFailedLoginAttempts() >= MAX_ATTEMPTS) {

            userRepository.lockAccount(
                    email,
                    LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES));
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void resetAttempts(String email) {
        userRepository.resetFailedAttempts(email);
    }
}
