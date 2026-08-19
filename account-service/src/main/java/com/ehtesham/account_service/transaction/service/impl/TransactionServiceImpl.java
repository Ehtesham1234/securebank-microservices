package com.ehtesham.account_service.transaction.service.impl;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.exception.InsufficientFundsException;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import com.ehtesham.account_service.exception.TransactionAlreadyReversedException;
import com.ehtesham.account_service.security.SecurityUtils;
import com.ehtesham.account_service.transaction.dto.DepositRequest;
import com.ehtesham.account_service.transaction.dto.TransactionResponse;
import com.ehtesham.account_service.transaction.dto.TransferRequest;
import com.ehtesham.account_service.transaction.dto.WithdrawRequest;
import com.ehtesham.account_service.transaction.entity.Transaction;
import com.ehtesham.account_service.transaction.enums.TransactionStatus;
import com.ehtesham.account_service.transaction.enums.TransactionType;
import com.ehtesham.account_service.transaction.repository.TransactionRepository;
import com.ehtesham.account_service.transaction.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/*
 * Follow-up fix #1: doDeposit/doWithdraw/doTransfer moved out to
 * MoneyMovementExecutor — see that class's javadoc for why. This class
 * now delegates to it via a genuine bean-to-bean call (goes through
 * Spring's proxy, unlike the old this.doDeposit(...) self-invocation),
 * and keeps the reversal flow + read-only queries, which were already
 * correct (reverseTransaction() is a real proxy entry point, so its
 * @Transactional already covered everything nested inside it).
 */
@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final IdempotencyHelper idempotencyHelper;
    private final SecurityUtils securityUtils;
    private final MoneyMovementExecutor moneyMovementExecutor;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            IdempotencyHelper idempotencyHelper,
            SecurityUtils securityUtils,
            MoneyMovementExecutor moneyMovementExecutor) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.idempotencyHelper = idempotencyHelper;
        this.securityUtils = securityUtils;
        this.moneyMovementExecutor = moneyMovementExecutor;
    }

    @Override
    public TransactionResponse deposit(
            Long accountId, DepositRequest request,
            String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "DEPOSIT",
                TransactionResponse.class,
                // Follow-up fix #1: genuine bean-to-bean call — goes
                // through moneyMovementExecutor's proxy, so its
                // @Transactional actually applies. The old version called
                // this.doDeposit(...) directly, which bypassed the proxy
                // entirely.
                () -> moneyMovementExecutor.doDeposit(
                        accountId, request, userId));
    }

    @Override
    public TransactionResponse withdraw(
            Long accountId, WithdrawRequest request,
            String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "WITHDRAW",
                TransactionResponse.class,
                () -> moneyMovementExecutor.doWithdraw(
                        accountId, request, userId));
    }

    @Override
    public TransactionResponse transfer(
            TransferRequest request, String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "TRANSFER",
                TransactionResponse.class,
                () -> moneyMovementExecutor.doTransfer(request, userId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactionHistory(
            Long accountId, Pageable pageable) {

        Long userId = securityUtils.getCurrentUserId();
        Account account = accountService
                .getOwnedAccount(accountId, userId);
        return transactionRepository
                .findByAccount(account, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllTransactions(
            Long userId, Pageable pageable) {
        return transactionRepository.findAllForAdmin(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public TransactionResponse reverseTransaction(
            Long transactionId) {

        Transaction original = transactionRepository
                .findByIdForUpdate(transactionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Transaction not found"));

        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new TransactionAlreadyReversedException(
                    "This transaction has already been reversed");
        }

        if (original.getType() == TransactionType.TRANSFER_OUT
                || original.getType() == TransactionType.TRANSFER_IN) {
            return reverseTransferPair(original);
        }

        return reverseSingleTransaction(original);
    }

    private TransactionResponse reverseTransferPair(
            Transaction original) {

        String pairedRef = original.getTransactionRef()
                .endsWith("-OUT")
                ? original.getTransactionRef().replace("-OUT", "-IN")
                : original.getTransactionRef().replace("-IN", "-OUT");

        Transaction paired = transactionRepository
                .findByTransactionRefForUpdate(pairedRef)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Paired transaction not found: "
                                        + pairedRef));

        if (paired.getStatus() == TransactionStatus.REVERSED) {
            throw new TransactionAlreadyReversedException(
                    "Paired transaction already reversed");
        }

        TransactionResponse result =
                reverseSingleTransaction(original);
        reverseSingleTransaction(paired);
        return result;
    }

    private Transaction doSingleReversal(Transaction original) {

        Account account = original.getAccount();
        BigDecimal reversalAmount = original.getAmount();
        BigDecimal newBalance;

        if (original.getType() == TransactionType.DEPOSIT
                || original.getType() ==
                TransactionType.TRANSFER_IN) {

            newBalance = account.getBalance()
                    .subtract(reversalAmount);

            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(
                        "Cannot reverse — insufficient balance");
            }
        } else {
            newBalance = account.getBalance()
                    .add(reversalAmount);
        }

        account.setBalance(newBalance);
        accountRepository.save(account);

        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        Transaction reversal = new Transaction();
        reversal.setTransactionRef(
                original.getTransactionRef() + "-REVERSAL");
        reversal.setAccount(account);
        reversal.setType(
                original.getType() == TransactionType.DEPOSIT
                        || original.getType() ==
                        TransactionType.TRANSFER_IN
                        ? TransactionType.WITHDRAW
                        : TransactionType.DEPOSIT);
        reversal.setAmount(reversalAmount);
        reversal.setBalanceAfter(newBalance);
        reversal.setStatus(TransactionStatus.SUCCESS);
        reversal.setDescription("Reversal of "
                + original.getTransactionRef());

        return transactionRepository.save(reversal);
    }

    private TransactionResponse reverseSingleTransaction(
            Transaction original) {
        return mapToResponse(doSingleReversal(original));
    }

    private TransactionResponse mapToResponse(
            Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .accountNumber(t.getAccount()
                        .getAccountNumber())
                .userId(t.getAccount().getUserId())
                .type(t.getType())
                .amount(t.getAmount())
                .balanceAfter(t.getBalanceAfter())
                .status(t.getStatus())
                .description(t.getDescription())
                .relatedAccountNumber(
                        t.getRelatedAccount() != null
                                ? t.getRelatedAccount()
                                .getAccountNumber()
                                : null)
                .createdAt(t.getCreatedAt())
                .build();
    }
}
