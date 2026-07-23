package com.ehtesham.account_service.transaction.service;

import com.ehtesham.account_service.transaction.dto.DepositRequest;
import com.ehtesham.account_service.transaction.dto.TransactionResponse;
import com.ehtesham.account_service.transaction.dto.TransferRequest;
import com.ehtesham.account_service.transaction.dto.WithdrawRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {

    // Removed: String email param — userId from SecurityContext
    TransactionResponse deposit(Long accountId,
                                DepositRequest request, String idempotencyKey);

    TransactionResponse withdraw(Long accountId,
                                 WithdrawRequest request, String idempotencyKey);

    TransactionResponse transfer(TransferRequest request,
                                 String idempotencyKey);

    // Removed: String email param
    Page<TransactionResponse> getTransactionHistory(
            Long accountId, Pageable pageable);

    Page<TransactionResponse> getAllTransactions(Pageable pageable);

    // Removed: String adminEmail param
    TransactionResponse reverseTransaction(Long transactionId);
}