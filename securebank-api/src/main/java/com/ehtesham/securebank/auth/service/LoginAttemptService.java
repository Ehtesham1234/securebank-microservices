package com.ehtesham.securebank.auth.service;

public interface LoginAttemptService {
    void recordFailedAttempt(String email);
    void resetAttempts(String email);
}
