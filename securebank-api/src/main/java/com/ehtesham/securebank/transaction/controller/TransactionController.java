package com.ehtesham.securebank.transaction.controller;

import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import com.ehtesham.securebank.transaction.dto.DepositRequest;
import com.ehtesham.securebank.transaction.dto.TransactionResponse;
import com.ehtesham.securebank.transaction.dto.TransferRequest;
import com.ehtesham.securebank.transaction.dto.WithdrawRequest;
import com.ehtesham.securebank.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/transaction")
@Tag(name = "Transactions", description = "Deposits, withdrawals, transfers")
public class TransactionController {


    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/accounts/{accountId}/deposit")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        TransactionResponse response = transactionService.deposit(
                accountId, request, principal.getUsername(), idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deposit successful", response));
    }

    @PostMapping("/accounts/{accountId}/withdraw")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        TransactionResponse response = transactionService.withdraw(
                accountId, request, principal.getUsername(), idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal successful", response));
    }

    @PostMapping("/transactions/transfer")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        TransactionResponse response = transactionService.transfer(
                request, principal.getUsername(), idempotencyKey);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transfer successful", response));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getTransactionHistory(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        Page<TransactionResponse> history = transactionService
                .getTransactionHistory(
                        accountId, principal.getUsername(), pageable);

        return ResponseEntity.ok(
                ApiResponse.success("Transaction history retrieved", history));
    }


    // TransactionController (or wherever admin endpoints live)
    @GetMapping("/admin/transactions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getAllTransactions(
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<TransactionResponse> transactions =
                transactionService.getAllTransactions(pageable);

        return ResponseEntity.ok(
                ApiResponse.success("All transactions retrieved", transactions));
    }

    @PostMapping("/admin/transactions/{id}/reverse")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverseTransaction(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        TransactionResponse response = transactionService
                .reverseTransaction(id, principal.getUsername());

        return ResponseEntity.ok(
                ApiResponse.success("Transaction reversed successfully", response));
    }
}
