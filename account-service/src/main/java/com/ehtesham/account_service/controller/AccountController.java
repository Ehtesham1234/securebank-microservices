package com.ehtesham.account_service.controller;

import com.ehtesham.account_service.dto.*;
import com.ehtesham.account_service.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ── CUSTOMER endpoints ────────────────────────────────────────
    // Note: userId comes from header set by API Gateway after JWT validation
    // API Gateway extracts userId from JWT and forwards it as X-User-Id header

    @PostMapping("/accounts/apply")
    public ResponseEntity<AccountResponse> applyForAccount(
            @Valid @RequestBody AccountApplicationRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.applyForAccount(
                        request, userId));
    }

    @GetMapping("/accounts")
    public ResponseEntity<List<AccountResponse>> getMyAccounts(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                accountService.getMyAccounts(userId));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<AccountResponse> getAccountById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                accountService.getAccountById(id, userId));
    }

    // ── ADMIN endpoints ───────────────────────────────────────────

    @GetMapping("/admin/accounts")
    public ResponseEntity<List<AccountResponse>> getAllAccounts() {
        return ResponseEntity.ok(
                accountService.getAllAccounts());
    }

    @PostMapping("/admin/accounts/{id}/freeze")
    public ResponseEntity<AccountResponse> freezeAccount(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                accountService.freezeAccount(id));
    }

    @PostMapping("/admin/accounts/{id}/unfreeze")
    public ResponseEntity<AccountResponse> unfreezeAccount(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                accountService.unfreezeAccount(id));
    }

    @PostMapping("/admin/accounts/{id}/close")
    public ResponseEntity<AccountResponse> closeAccount(
            @PathVariable Long id) {
        return ResponseEntity.ok(
                accountService.closeAccount(id));
    }

    // ── INTERNAL endpoint (for loan-service via OpenFeign) ────────

    @GetMapping("/internal/accounts/{accountId}/validate")
    public ResponseEntity<AccountValidationResponse> validateAccount(
            @PathVariable Long accountId,
            @RequestParam Long userId) {

        return ResponseEntity.ok(
                accountService.validateAccount(accountId, userId));
    }

    // ── INTERNAL: create savings account (called by kyc flow) ────

    @PostMapping("/internal/accounts/savings")
    public ResponseEntity<AccountResponse> createSavingsAccount(
            @RequestParam Long userId,
            @RequestParam String firstName,
            @RequestParam String lastName) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountService.createSavingsAccount(
                        userId, firstName, lastName));
    }
}