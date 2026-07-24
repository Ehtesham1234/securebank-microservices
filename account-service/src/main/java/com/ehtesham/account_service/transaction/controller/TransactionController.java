package com.ehtesham.account_service.transaction.controller;

import com.ehtesham.account_service.common.response.ApiResponse;
import com.ehtesham.account_service.transaction.dto.DepositRequest;
import com.ehtesham.account_service.transaction.dto.TransactionResponse;
import com.ehtesham.account_service.transaction.dto.TransferRequest;
import com.ehtesham.account_service.transaction.dto.WithdrawRequest;
import com.ehtesham.account_service.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transactions",
        description = "Deposit, withdraw, transfer with idempotency")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/transactions/accounts/{accountId}/deposit")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @PathVariable Long accountId,
            @Valid @RequestBody DepositRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransactionResponse response = transactionService.deposit(accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Deposit successful", response, 201));
    }

    @PostMapping("/transactions/accounts/{accountId}/withdraw")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @PathVariable Long accountId,
            @Valid @RequestBody WithdrawRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransactionResponse response = transactionService.withdraw(accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Withdrawal successful", response, 201));
    }

    @PostMapping("/transactions/transfer")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        TransactionResponse response = transactionService.transfer(request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Transfer successful", response, 201));
    }

    @GetMapping("/transactions/accounts/{accountId}")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> history(
            @PathVariable Long accountId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<TransactionResponse> history = transactionService.getTransactionHistory(accountId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Transaction history retrieved", history));
    }

    @GetMapping("/admin/transactions")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> allTransactions(
            @PageableDefault(size = 20) Pageable pageable) {

        Page<TransactionResponse> transactions = transactionService.getAllTransactions(pageable);
        return ResponseEntity.ok(ApiResponse.success("All transactions retrieved", transactions));
    }

    @PostMapping("/admin/transactions/{id}/reverse")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverse(
            @PathVariable Long id) {

        TransactionResponse response = transactionService.reverseTransaction(id);
        return ResponseEntity.ok(ApiResponse.success("Transaction reversed successfully", response));
    }
}
