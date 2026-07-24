package com.ehtesham.account_service.account.controller;

import com.ehtesham.account_service.account.dto.*;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounts",
        description = "Account management — create, view, freeze, close")

public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/accounts/apply")
    public ResponseEntity<ApiResponse<AccountResponse>> applyForAccount(
            @Valid @RequestBody AccountApplicationRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        AccountResponse response = accountService.applyForAccount(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Account application submitted", response, 201));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched customer accounts",
                accountService.getMyAccounts(userId)
        ));
    }

    @GetMapping("/accounts/{id}")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(ApiResponse.success(
                "Fetched account details",
                accountService.getAccountById(id, userId)
        ));
    }

    // ── ADMIN endpoints ───────────────────────────────────────────

    @GetMapping("/admin/accounts")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched all accounts",
                accountService.getAllAccounts()
        ));
    }

    @PostMapping("/admin/accounts/{id}/freeze")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account frozen successfully",
                accountService.freezeAccount(id)
        ));
    }

    @PostMapping("/admin/accounts/{id}/unfreeze")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account unfrozen successfully",
                accountService.unfreezeAccount(id)
        ));
    }

    @PostMapping("/admin/accounts/{id}/close")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Account closed successfully",
                accountService.closeAccount(id)
        ));
    }
}
