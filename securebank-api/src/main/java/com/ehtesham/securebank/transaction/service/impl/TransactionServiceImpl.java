package com.ehtesham.securebank.transaction.service.impl;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.audit.annotation.Auditable;
import com.ehtesham.securebank.common.enums.AccountStatus;
import com.ehtesham.securebank.common.enums.TransactionStatus;
import com.ehtesham.securebank.common.enums.TransactionType;
import com.ehtesham.securebank.common.exception.AccountOperationException;
import com.ehtesham.securebank.common.exception.InsufficientFundsException;
import com.ehtesham.securebank.common.exception.ResourceNotFoundException;
import com.ehtesham.securebank.common.exception.TransactionAlreadyReversedException;
import com.ehtesham.securebank.transaction.dto.*;
import com.ehtesham.securebank.transaction.entity.Transaction;
import com.ehtesham.securebank.transaction.repository.TransactionRepository;
import com.ehtesham.securebank.transaction.service.TransactionService;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import com.ehtesham.securebank.websocket.service.WebSocketNotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class TransactionServiceImpl implements TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final AccountService accountService;
    private final UserRepository userRepository;
    private final IdempotencyHelper idempotencyHelper;
    private final WebSocketNotificationService wsNotificationService;
    public TransactionServiceImpl(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            AccountService accountService,
            UserRepository userRepository,
            IdempotencyHelper idempotencyHelper, WebSocketNotificationService wsNotificationService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.accountService = accountService;
        this.userRepository = userRepository;
        this.idempotencyHelper = idempotencyHelper;
        this.wsNotificationService = wsNotificationService;
    }

    @Auditable(action = "DEPOSIT")
    @Override
    public TransactionResponse deposit(
            Long accountId, DepositRequest request,
            String email, String idempotencyKey) {

        User user = getUser(email);

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, user, "DEPOSIT", TransactionResponse.class,
                () -> doDeposit(accountId, request, user));
    }



    @Transactional
    protected TransactionResponse doDeposit(
            Long accountId, DepositRequest request, User user) {

        Account account = accountService.getOwnedAccount(accountId, user);

        validateAccountActive(account);

        BigDecimal newBalance = account.getBalance().add(request.getAmount());
        account.setBalance(newBalance);

        // @Version field is checked automatically by Hibernate
        // on THIS save() call — no manual code needed for that part
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
        // Push live balance update to customer's browser
        wsNotificationService.sendBalanceUpdate(
                user.getId(),
                account.getAccountNumber(),
                newBalance,
                request.getAmount(),
                TransactionType.DEPOSIT,
                saved.getTransactionRef(),
                request.getDescription());
        return mapToResponse(saved);
    }

    // ... withdraw, transfer, getTransactionHistory, and private
    @Auditable(action = "WITHDRAW")
    @Override
    public TransactionResponse withdraw(
            Long accountId, WithdrawRequest request,
            String email, String idempotencyKey) {

        User user = getUser(email);

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, user, "WITHDRAW", TransactionResponse.class,
                () -> doWithdraw(accountId, request, user));
    }

    @Transactional
    protected TransactionResponse doWithdraw(
            Long accountId, WithdrawRequest request, User user) {

        Account account = accountService.getOwnedAccount(accountId, user);

        validateAccountActive(account);

        if (account.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                    "Insufficient balance for this withdrawal");
        }

        BigDecimal newBalance = account.getBalance().subtract(request.getAmount());
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
        // Push live balance update to customer's browser
        wsNotificationService.sendBalanceUpdate(
                user.getId(),
                account.getAccountNumber(),
                newBalance,
                request.getAmount(),
                TransactionType.WITHDRAW,
                saved.getTransactionRef(),
                request.getDescription());
        return mapToResponse(saved);
    }
    @Auditable(action = "TRANSFER")
    @Override
    public TransactionResponse transfer(
            TransferRequest request,
            String email, String idempotencyKey) {

        User user = getUser(email);

        return idempotencyHelper.executeIdempotently(
                idempotencyKey, user, "TRANSFER", TransactionResponse.class,
                () -> doTransfer(request, user));
    }

    @Transactional
    protected TransactionResponse doTransfer(
            TransferRequest request, User user) {

        if (request.getFromAccountNumber()
                .equals(request.getToAccountNumber())) {
            throw new AccountOperationException(
                    "Cannot transfer to the same account");
        }

        Account fromAccount = accountRepository
                .findByAccountNumber(request.getFromAccountNumber())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Source account not found"));

        if (!fromAccount.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Source account not found");
        }

        Account toAccount = accountRepository
                .findByAccountNumber(request.getToAccountNumber())
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

        Transaction savedOutgoing = transactionRepository.save(outgoing);

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

        // reuse savedOutgoing directly — DO NOT call save() again
        // just to "re-fetch" it. account and relatedAccount are
        // ALREADY fully-loaded real objects on this exact instance,
        // set just a few lines above, no proxy involved at all.

        // Push to SENDER
        wsNotificationService.sendBalanceUpdate(
                user.getId(),
                fromAccount.getAccountNumber(),
                fromNewBalance,
                request.getAmount(),
                TransactionType.TRANSFER_OUT,
                sharedRef + "-OUT",
                request.getDescription());

        // Push to RECEIVER
        // Need to find receiver's userId
        toAccount.getUser(); // lazy load — we're inside @Transactional
        wsNotificationService.sendBalanceUpdate(
                toAccount.getUser().getId(),
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
            Long accountId, String email, Pageable pageable) {

        User user = getUser(email);

        Account account = accountService.getOwnedAccount(accountId, user);

        Page<Transaction> transactionPage = transactionRepository
                .findByAccount(account, pageable);

        return transactionPage.map(this::mapToResponse);
    }
    // TransactionServiceImpl
    @Override
    @Transactional(readOnly = true)
    public Page<TransactionResponse> getAllTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public TransactionResponse reverseTransaction(
            Long transactionId, String adminEmail) {

        Transaction original = transactionRepository.findById(transactionId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Transaction not found"));

        if (original.getStatus() == TransactionStatus.REVERSED) {
            throw new TransactionAlreadyReversedException(
                    "This transaction has already been reversed");
        }

        // handle transfer pairing atomically
        if (original.getType() == TransactionType.TRANSFER_OUT
                || original.getType() == TransactionType.TRANSFER_IN) {

            return reverseTransferPair(original);
        }

        // DEPOSIT or WITHDRAW — simple single-account reversal
        return reverseSingleTransaction(original);
    }
    private TransactionResponse reverseTransferPair(Transaction original) {

        // find the paired transaction (the other half of this transfer)
        String pairedRef;
        if (original.getTransactionRef().endsWith("-OUT")) {
            pairedRef = original.getTransactionRef()
                    .replace("-OUT", "-IN");
        } else {
            pairedRef = original.getTransactionRef()
                    .replace("-IN", "-OUT");
        }

        Transaction paired = transactionRepository
                .findByTransactionRef(pairedRef)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Paired transfer transaction not found: " + pairedRef));

        if (paired.getStatus() == TransactionStatus.REVERSED) {
            throw new TransactionAlreadyReversedException(
                    "The paired transfer transaction has already been reversed");
        }

        // reverse BOTH sides in the same @Transactional call
        TransactionResponse reversalOut = reverseSingleTransaction(original);
        reverseSingleTransaction(paired);

        return reversalOut;
    }
    private Transaction doSingleReversal(Transaction original) {

        Account account = original.getAccount();
        BigDecimal reversalAmount = original.getAmount();
        BigDecimal newBalance;

        if (original.getType() == TransactionType.DEPOSIT
                || original.getType() == TransactionType.TRANSFER_IN) {

            newBalance = account.getBalance().subtract(reversalAmount);

            if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                throw new InsufficientFundsException(
                        "Cannot reverse transaction " +
                                original.getTransactionRef() +
                                " — account " + account.getAccountNumber() +
                                " has insufficient balance (current: ₹" +
                                account.getBalance().toPlainString() +
                                ", reversal requires: ₹" +
                                reversalAmount.toPlainString() + ")");
            }
        } else {
            newBalance = account.getBalance().add(reversalAmount);
        }

        account.setBalance(newBalance);
        accountRepository.save(account);

        original.setStatus(TransactionStatus.REVERSED);
        transactionRepository.save(original);

        Transaction reversalRecord = new Transaction();
        reversalRecord.setTransactionRef(
                original.getTransactionRef() + "-REVERSAL");
        reversalRecord.setAccount(account);
        reversalRecord.setType(
                original.getType() == TransactionType.DEPOSIT
                        || original.getType() == TransactionType.TRANSFER_IN
                        ? TransactionType.WITHDRAW
                        : TransactionType.DEPOSIT);
        reversalRecord.setAmount(reversalAmount);
        reversalRecord.setBalanceAfter(newBalance);
        reversalRecord.setStatus(TransactionStatus.SUCCESS);
        reversalRecord.setDescription(
                "Reversal of transaction " + original.getTransactionRef());

        return transactionRepository.save(reversalRecord);
    }

    private TransactionResponse reverseSingleTransaction(Transaction original) {
        return mapToResponse(doSingleReversal(original));
    }
    //     helpers continue below
    private void validateAccountActive(Account account) {

        if (account.getAccountStatus() == AccountStatus.FROZEN) {
            throw new AccountOperationException(
                    "This account is frozen. Contact support.");
        }

        if (account.getAccountStatus() == AccountStatus.CLOSED) {
            throw new AccountOperationException(
                    "This account has been closed.");
        }

        if (account.getAccountStatus() == AccountStatus.DORMANT) {
            throw new AccountOperationException(
                    "This account is dormant. Please reactivate it first.");
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
        } while (transactionRepository.existsByTransactionRef(ref));

        return ref;
    }
    private User getUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("User not found"));
    }

    private TransactionResponse mapToResponse(Transaction transaction) {
        return TransactionResponse.builder()
                .id(transaction.getId())
                .transactionRef(transaction.getTransactionRef())
                .accountNumber(transaction.getAccount().getAccountNumber())
                .type(transaction.getType())
                .amount(transaction.getAmount())
                .balanceAfter(transaction.getBalanceAfter())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .relatedAccountNumber(
                        transaction.getRelatedAccount() != null
                                ? transaction.getRelatedAccount().getAccountNumber()
                                : null)
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}