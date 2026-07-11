package com.ehtesham.securebank.scheduler.controller;

import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.scheduler.service.BankingScheduler;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/scheduler")
@Tag(name = "Scheduler", description = "Manual triggers for scheduled jobs")
public class SchedulerController {

    private final BankingScheduler bankingScheduler;

    public SchedulerController(BankingScheduler bankingScheduler) {
        this.bankingScheduler = bankingScheduler;
    }

    @PostMapping("/credit-card-statements")
    public ResponseEntity<ApiResponse<Void>> triggerStatements() {
        bankingScheduler.generateCreditCardStatements();
        return ResponseEntity.ok(ApiResponse.success(
                "Statement generation triggered"));
    }

    @PostMapping("/overdue-emis")
    public ResponseEntity<ApiResponse<Void>> triggerOverdueCheck() {
        bankingScheduler.markOverdueEmiPayments();
        return ResponseEntity.ok(ApiResponse.success(
                "Overdue EMI check triggered"));
    }

    @PostMapping("/cleanup-otps")
    public ResponseEntity<ApiResponse<Void>> triggerOtpCleanup() {
        bankingScheduler.cleanupExpiredOtps();
        return ResponseEntity.ok(ApiResponse.success(
                "OTP cleanup triggered"));
    }

    @PostMapping("/cleanup-tokens")
    public ResponseEntity<ApiResponse<Void>> triggerTokenCleanup() {
        bankingScheduler.cleanupExpiredRefreshTokens();
        return ResponseEntity.ok(ApiResponse.success(
                "Token cleanup triggered"));
    }
    @PostMapping("/expired-cards")
    public ResponseEntity<ApiResponse<Void>> triggerMarkExpiredCards() {
        bankingScheduler.markExpiredCards();
        return ResponseEntity.ok(ApiResponse.success(
                "Expired Cards triggered"));
    }
}