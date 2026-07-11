package com.ehtesham.securebank.transaction.service;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.common.enums.AccountStatus;
import com.ehtesham.securebank.common.enums.AccountType;
import com.ehtesham.securebank.common.exception.InsufficientFundsException;
import com.ehtesham.securebank.transaction.dto.DepositRequest;
import com.ehtesham.securebank.transaction.dto.TransactionResponse;
import com.ehtesham.securebank.transaction.dto.WithdrawRequest;
import com.ehtesham.securebank.transaction.entity.IdempotencyKey;
import com.ehtesham.securebank.transaction.repository.IdempotencyKeyRepository;
import com.ehtesham.securebank.transaction.repository.TransactionRepository;
import com.ehtesham.securebank.transaction.service.impl.IdempotencyHelper;
import com.ehtesham.securebank.transaction.service.impl.TransactionServiceImpl;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TransactionService Tests")
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private AccountService accountService;
    @Mock private UserRepository userRepository;
    @Mock private IdempotencyKeyRepository idempotencyKeyRepository;

    private IdempotencyHelper idempotencyHelper;
    private TransactionServiceImpl transactionService;

    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();

        idempotencyHelper = new IdempotencyHelper(
                idempotencyKeyRepository, objectMapper);

        transactionService = new TransactionServiceImpl(
                transactionRepository,
                accountRepository,
                accountService,
                userRepository,
                idempotencyHelper);

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setFirstName("Test");
        testUser.setLastName("User");

        testAccount = new Account();
        testAccount.setId(1L);
        testAccount.setAccountNumber("SB1234567890");
        testAccount.setAccountType(AccountType.SAVINGS);
        testAccount.setAccountStatus(AccountStatus.ACTIVE);
        testAccount.setBalance(new BigDecimal("1000.00"));
        testAccount.setUser(testUser);
        testAccount.setVersion(0L);
    }

    @Nested
    @DisplayName("Deposit Tests")
    class DepositTests {

        @Test
        @DisplayName("Should deposit successfully and increase balance")
        void deposit_success() {
            // ARRANGE
            DepositRequest request = new DepositRequest();
            request.setAmount(new BigDecimal("500.00"));
            request.setDescription("test deposit");

            String idempotencyKey = UUID.randomUUID().toString();

            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(1L, testUser))
                    .thenReturn(testAccount);
            when(idempotencyKeyRepository
                    .findByIdempotencyKeyAndUserAndOperationType(
                            idempotencyKey, testUser, "DEPOSIT"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.existsByTransactionRef(anyString()))
                    .thenReturn(false);
            when(accountRepository.save(any(Account.class)))
                    .thenReturn(testAccount);
            when(transactionRepository.save(any()))
                    .thenAnswer(inv -> {
                        var t = inv.getArgument(0,
                                com.ehtesham.securebank.transaction
                                        .entity.Transaction.class);
                        t.setId(1L);
                        return t;
                    });
            when(idempotencyKeyRepository.save(any()))
                    .thenReturn(new IdempotencyKey());

            // ACT
            TransactionResponse response = transactionService.deposit(
                    1L, request, testUser.getEmail(), idempotencyKey);

            // ASSERT
            assertThat(response).isNotNull();
            assertThat(response.getAmount())
                    .isEqualByComparingTo("500.00");
            assertThat(testAccount.getBalance())
                    .isEqualByComparingTo("1500.00");

            verify(accountRepository).save(testAccount);
            verify(transactionRepository).save(any());
        }

        @Test
        @DisplayName("Should return cached response for duplicate idempotency key")
        void deposit_idempotencyReturnsCachedResponse() throws Exception {
            // ARRANGE
            String idempotencyKey = UUID.randomUUID().toString();

            TransactionResponse cachedResponse = TransactionResponse.builder()
                    .id(1L)
                    .transactionRef("TXN123-CACHED")
                    .amount(new BigDecimal("500.00"))
                    .build();

            ObjectMapper mapper = new ObjectMapper();
            mapper.findAndRegisterModules();

            IdempotencyKey existingKey = new IdempotencyKey();
            existingKey.setIdempotencyKey(idempotencyKey);
            existingKey.setOperationType("DEPOSIT");
            existingKey.setResponseBody(
                    mapper.writeValueAsString(cachedResponse));

            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(idempotencyKeyRepository
                    .findByIdempotencyKeyAndUserAndOperationType(
                            idempotencyKey, testUser, "DEPOSIT"))
                    .thenReturn(Optional.of(existingKey));

            // ACT
            DepositRequest request = new DepositRequest();
            request.setAmount(new BigDecimal("500.00"));

            TransactionResponse response = transactionService.deposit(
                    1L, request, testUser.getEmail(), idempotencyKey);

            // ASSERT — cached response returned, no new deposit processed
            assertThat(response.getTransactionRef())
                    .isEqualTo("TXN123-CACHED");

            // CRITICAL: account save and transaction save
            // should NEVER have been called for a duplicate key
            verify(accountRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Withdraw Tests")
    class WithdrawTests {

        @Test
        @DisplayName("Should withdraw successfully when balance is sufficient")
        void withdraw_success() {
            // ARRANGE
            WithdrawRequest request = new WithdrawRequest();
            request.setAmount(new BigDecimal("400.00"));

            String idempotencyKey = UUID.randomUUID().toString();

            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(1L, testUser))
                    .thenReturn(testAccount);
            when(idempotencyKeyRepository
                    .findByIdempotencyKeyAndUserAndOperationType(
                            idempotencyKey, testUser, "WITHDRAW"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.existsByTransactionRef(anyString()))
                    .thenReturn(false);
            when(accountRepository.save(any())).thenReturn(testAccount);
            when(transactionRepository.save(any()))
                    .thenAnswer(inv -> {
                        var t = inv.getArgument(0,
                                com.ehtesham.securebank.transaction
                                        .entity.Transaction.class);
                        t.setId(2L);
                        return t;
                    });
            when(idempotencyKeyRepository.save(any()))
                    .thenReturn(new IdempotencyKey());

            // ACT
            TransactionResponse response = transactionService.withdraw(
                    1L, request, testUser.getEmail(), idempotencyKey);

            // ASSERT
            assertThat(response.getAmount())
                    .isEqualByComparingTo("400.00");
            assertThat(testAccount.getBalance())
                    .isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("Should throw InsufficientFundsException " +
                "when balance is too low")
        void withdraw_insufficientFunds() {
            // ARRANGE
            WithdrawRequest request = new WithdrawRequest();
            request.setAmount(new BigDecimal("5000.00")); // more than 1000

            String idempotencyKey = UUID.randomUUID().toString();

            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(1L, testUser))
                    .thenReturn(testAccount);
            when(idempotencyKeyRepository
                    .findByIdempotencyKeyAndUserAndOperationType(
                            idempotencyKey, testUser, "WITHDRAW"))
                    .thenReturn(Optional.empty());

            // ACT + ASSERT
            assertThatThrownBy(() ->
                    transactionService.withdraw(
                            1L, request, testUser.getEmail(),
                            idempotencyKey))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient balance");

            // Balance must NOT have changed
            assertThat(testAccount.getBalance())
                    .isEqualByComparingTo("1000.00");

            // Nothing should have been saved
            verify(accountRepository, never()).save(any());
            verify(transactionRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow withdrawing exact full balance")
        void withdraw_exactBalance() {
            // ARRANGE
            WithdrawRequest request = new WithdrawRequest();
            request.setAmount(new BigDecimal("1000.00")); // exactly the balance

            String idempotencyKey = UUID.randomUUID().toString();

            when(userRepository.findByEmail(testUser.getEmail()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(1L, testUser))
                    .thenReturn(testAccount);
            when(idempotencyKeyRepository
                    .findByIdempotencyKeyAndUserAndOperationType(
                            idempotencyKey, testUser, "WITHDRAW"))
                    .thenReturn(Optional.empty());
            when(transactionRepository.existsByTransactionRef(anyString()))
                    .thenReturn(false);
            when(accountRepository.save(any())).thenReturn(testAccount);
            when(transactionRepository.save(any()))
                    .thenAnswer(inv -> {
                        var t = inv.getArgument(0,
                                com.ehtesham.securebank.transaction
                                        .entity.Transaction.class);
                        t.setId(3L);
                        return t;
                    });
            when(idempotencyKeyRepository.save(any()))
                    .thenReturn(new IdempotencyKey());

            // ACT
            transactionService.withdraw(
                    1L, request, testUser.getEmail(), idempotencyKey);

            // ASSERT
            assertThat(testAccount.getBalance())
                    .isEqualByComparingTo("0.00");
        }
    }
}