package com.ehtesham.securebank.transaction.service;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.transaction.dto.DepositRequest;
import com.ehtesham.securebank.transaction.dto.TransactionResponse;
import com.ehtesham.securebank.transaction.dto.TransferRequest;
import com.ehtesham.securebank.transaction.dto.WithdrawRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface TransactionService {

    TransactionResponse deposit(
            Long accountId, DepositRequest request,
            String email, String idempotencyKey);

    TransactionResponse withdraw(
            Long accountId, WithdrawRequest request,
            String email, String idempotencyKey);

    TransactionResponse transfer(
            TransferRequest request,
            String email, String idempotencyKey);

    Page<TransactionResponse> getTransactionHistory(
            Long accountId, String email, Pageable pageable);

    // TransactionService interface — add this
    Page<TransactionResponse> getAllTransactions(Pageable pageable);

    // TransactionService interface
    TransactionResponse reverseTransaction(Long transactionId, String adminEmail);
}