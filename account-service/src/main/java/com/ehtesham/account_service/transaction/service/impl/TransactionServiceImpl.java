package com.ehtesham.account_service.transaction.service.impl;


import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.exception.AccountOperationException;
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
import com.ehtesham.account_service.transaction.publisher.TransactionEventPublisher;
import com.ehtesham.account_service.transaction.repository.TransactionRepository;
import com.ehtesham.account_service.transaction.service.TransactionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final IdempotencyHelper idempotencyHelper;
    private final TransactionEventPublisher eventPublisher;
    private final SecurityUtils securityUtils;

    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            IdempotencyHelper idempotencyHelper,
            TransactionEventPublisher eventPublisher,
            SecurityUtils securityUtils) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.idempotencyHelper = idempotencyHelper;
        this.eventPublisher = eventPublisher;
        this.securityUtils = securityUtils;
    }

    @Override
    public TransactionResponse deposit(
            Long accountId, DepositRequest request,
            String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "DEPOSIT",
                TransactionResponse.class,
                () -> doDeposit(accountId, request, userId));
    }

    @Transactional
    protected TransactionResponse doDeposit(
            Long accountId, DepositRequest request, Long userId) {

        Account account = accountService
                .getOwnedAccount(accountId, userId);
        validateAccountActive(account);

        BigDecimal newBalance = account.getBalance()
                .add(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(generateTransactionRef());
        transaction.setAccount(account);
        transaction.setType(TransactionType.DEPOSIT);
        transaction.setAmount(request.getAmount());
        transaction.setBalanceAfter(newBalance);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(request.getDescription());

        Transaction saved = transactionRepository.save(transaction);

        // Publish to Kafka → securebank-api → WebSocket
        eventPublisher.publishTransactionCompleted(
                userId,
                account.getAccountNumber(),
                newBalance,
                request.getAmount(),
                TransactionType.DEPOSIT,
                saved.getTransactionRef(),
                request.getDescription());

        return mapToResponse(saved);
    }

    @Override
    public TransactionResponse withdraw(
            Long accountId, WithdrawRequest request,
            String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "WITHDRAW",
                TransactionResponse.class,
                () -> doWithdraw(accountId, request, userId));
    }

    @Transactional
    protected TransactionResponse doWithdraw(
            Long accountId, WithdrawRequest request, Long userId) {

        Account account = accountService
                .getOwnedAccount(accountId, userId);
        validateAccountActive(account);

        if (account.getBalance()
                .compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for this withdrawal");
        }

        BigDecimal newBalance = account.getBalance()
                .subtract(request.getAmount());
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction transaction = new Transaction();
        transaction.setTransactionRef(generateTransactionRef());
        transaction.setAccount(account);
        transaction.setType(TransactionType.WITHDRAW);
        transaction.setAmount(request.getAmount());
        transaction.setBalanceAfter(newBalance);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setDescription(request.getDescription());

        Transaction saved = transactionRepository.save(transaction);

        eventPublisher.publishTransactionCompleted(
                userId,
                account.getAccountNumber(),
                newBalance,
                request.getAmount(),
                TransactionType.WITHDRAW,
                saved.getTransactionRef(),
                request.getDescription());

        return mapToResponse(saved);
    }

    @Override
    public TransactionResponse transfer(
            TransferRequest request, String idempotencyKey) {

        Long userId = securityUtils.getCurrentUserId();

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, userId, "TRANSFER",
                TransactionResponse.class,
                () -> doTransfer(request, userId));
    }

    @Transactional
    protected TransactionResponse doTransfer(
            TransferRequest request, Long userId) {

        if (request.getFromAccountNumber()
                .equals(request.getToAccountNumber())) {
            throw new AccountOperationException(
                    "Cannot transfer to the same account");
        }

        Account fromAccount = accountRepository
                .findByAccountNumber(
                        request.getFromAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Source account not found"));

        // Verify ownership — userId must match account's userId
        if (!fromAccount.getUserId().equals(userId)) {
            throw new ResourceNotFoundException(
                    "Source account not found");
        }

        Account toAccount = accountRepository
                .findByAccountNumber(
                        request.getToAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Destination account not found"));

        validateAccountActive(fromAccount);
        validateAccountActive(toAccount);

        if (fromAccount.getBalance()
                .compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for this transfer");
        }

        BigDecimal fromNewBalance = fromAccount.getBalance()
                .subtract(request.getAmount());
        BigDecimal toNewBalance = toAccount.getBalance()
                .add(request.getAmount());

        fromAccount.setBalance(fromNewBalance);
        toAccount.setBalance(toNewBalance);
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        String sharedRef = generateTransactionRef();

        Transaction outgoing = new Transaction();
        outgoing.setTransactionRef(sharedRef + "-OUT");
        outgoing.setAccount(fromAccount);
        outgoing.setType(TransactionType.TRANSFER_OUT);
        outgoing.setAmount(request.getAmount());
        outgoing.setBalanceAfter(fromNewBalance);
        outgoing.setStatus(TransactionStatus.SUCCESS);
        outgoing.setDescription(request.getDescription());
        outgoing.setRelatedAccount(toAccount);
        Transaction savedOutgoing =
                transactionRepository.save(outgoing);

        Transaction incoming = new Transaction();
        incoming.setTransactionRef(sharedRef + "-IN");
        incoming.setAccount(toAccount);
        incoming.setType(TransactionType.TRANSFER_IN);
        incoming.setAmount(request.getAmount());
        incoming.setBalanceAfter(toNewBalance);
        incoming.setStatus(TransactionStatus.SUCCESS);
        incoming.setDescription(request.getDescription());
        incoming.setRelatedAccount(fromAccount);
        transactionRepository.save(incoming);

        // Push to SENDER
        eventPublisher.publishTransactionCompleted(
                userId,
                fromAccount.getAccountNumber(),
                fromNewBalance,
                request.getAmount(),
                TransactionType.TRANSFER_OUT,
                sharedRef + "-OUT",
                request.getDescription());

        // Push to RECEIVER — use toAccount.getUserId()
        // (plain Long field on Account entity, no lazy load)
        eventPublisher.publishTransactionCompleted(
                toAccount.getUserId(),
                toAccount.getAccountNumber(),
                toNewBalance,
                request.getAmount(),
                TransactionType.TRANSFER_IN,
                sharedRef + "-IN",
                request.getDescription());

        return mapToResponse(savedOutgoing);
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
            Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public TransactionResponse reverseTransaction(
            Long transactionId) {

        Transaction original = transactionRepository
                .findById(transactionId)
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
                .findByTransactionRef(pairedRef)
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

    private void validateAccountActive(Account account) {
        switch (account.getAccountStatus()) {
            case FROZEN -> throw new AccountOperationException(
                    "This account is frozen. Contact support.");
            case CLOSED -> throw new AccountOperationException(
                    "This account has been closed.");
            case DORMANT -> throw new AccountOperationException(
                    "This account is dormant.");
            default -> { /* ACTIVE — allow */ }
        }
    }

    private String generateTransactionRef() {
        String ref;
        do {
            ref = "TXN" + UUID.randomUUID()
                    .toString()
                    .replace("-", "")
                    .substring(0, 12)
                    .toUpperCase();
        } while (transactionRepository
                .existsByTransactionRef(ref));
        return ref;
    }

    private TransactionResponse mapToResponse(
            Transaction t) {
        return TransactionResponse.builder()
                .id(t.getId())
                .transactionRef(t.getTransactionRef())
                .accountNumber(t.getAccount()
                        .getAccountNumber())
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