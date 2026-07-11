package com.ehtesham.securebank.account.controller;

import com.ehtesham.securebank.account.dto.AccountApplicationRequest;
import com.ehtesham.securebank.account.dto.AccountResponse;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounts", description = "Account management and operations")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    // ── CUSTOMER endpoints ───────────────────────────────────────

    @PostMapping("/accounts/apply")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<AccountResponse>> applyForAccount(
            @Valid @RequestBody AccountApplicationRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        AccountResponse response = accountService.applyForAccount(
                request, userDetails.getUsername());

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Account application submitted", response));
    }

    @GetMapping("/accounts")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getMyAccounts(
            @AuthenticationPrincipal UserDetails userDetails) {

        List<AccountResponse> accounts =
                accountService.getMyAccounts(
                        userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("Accounts retrieved", accounts));
    }

    @GetMapping("/accounts/{id}")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccountById(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        AccountResponse response = accountService.getAccountById(
                id, userDetails.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("Account retrieved", response));
    }

    // ── ADMIN endpoints ──────────────────────────────────────────

    @GetMapping("/admin/accounts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getAllAccounts() {

        List<AccountResponse> accounts =
                accountService.getAllAccounts();

        return ResponseEntity.ok(
                ApiResponse.success("All accounts retrieved", accounts));
    }

    @PostMapping("/admin/accounts/{id}/freeze")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @PathVariable Long id) {

        AccountResponse response = accountService.freezeAccount(id);

        return ResponseEntity.ok(
                ApiResponse.success("Account frozen", response));
    }

    @PostMapping("/admin/accounts/{id}/unfreeze")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(
            @PathVariable Long id) {

        AccountResponse response =
                accountService.unfreezeAccount(id);

        return ResponseEntity.ok(
                ApiResponse.success("Account unfrozen", response));
    }

    @PostMapping("/admin/accounts/{id}/close")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(
            @PathVariable Long id) {

        AccountResponse response = accountService.closeAccount(id);

        return ResponseEntity.ok(
                ApiResponse.success("Account closed", response));
    }
}