package com.ehtesham.securebank.loan.service;

import com.ehtesham.securebank.account.entity.Account;
import com.ehtesham.securebank.account.repository.AccountRepository;
import com.ehtesham.securebank.account.service.AccountService;
import com.ehtesham.securebank.common.enums.*;
import com.ehtesham.securebank.common.exception.AccountOperationException;
import com.ehtesham.securebank.common.exception.InsufficientFundsException;
import com.ehtesham.securebank.loan.dto.LoanApplicationRequest;
import com.ehtesham.securebank.loan.dto.LoanResponse;
import com.ehtesham.securebank.loan.dto.LoanReviewRequest;
import com.ehtesham.securebank.loan.entity.Loan;
import com.ehtesham.securebank.loan.repository.EmiPaymentRepository;
import com.ehtesham.securebank.loan.repository.LoanRepository;
import com.ehtesham.securebank.loan.service.impl.LoanServiceImpl;
import com.ehtesham.securebank.user.entity.User;
import com.ehtesham.securebank.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LoanService Tests")
class LoanServiceTest {

    @Mock private LoanRepository loanRepository;
    @Mock private EmiPaymentRepository emiPaymentRepository;
    @Mock private UserRepository userRepository;
    @Mock private AccountService accountService;
    @Mock private AccountRepository accountRepository;

    private LoanServiceImpl loanService;
    private User testUser;
    private Account testAccount;

    @BeforeEach
    void setUp() {
        loanService = new LoanServiceImpl(
                loanRepository,
                emiPaymentRepository,
                userRepository,
                accountService,
                accountRepository,
                null); // TransactionService not needed for these tests

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
        testAccount.setBalance(new BigDecimal("500000.00"));
        testAccount.setUser(testUser);
        testAccount.setVersion(0L);
    }

    @Nested
    @DisplayName("EMI Calculation Tests")
    class EmiCalculationTests {

        @Test
        @DisplayName("Personal loan EMI should be approximately ₹8,885 " +
                "for ₹1,00,000 at 12% for 12 months")
        void emi_personalLoan_correctAmount() {
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(any(), any()))
                    .thenReturn(testAccount);
            when(loanRepository.existsByUserAndStatusIn(any(), any()))
                    .thenReturn(false);
            when(loanRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            LoanApplicationRequest request = buildRequest(
                    LoanType.PERSONAL_LOAN,
                    new BigDecimal("100000"),
                    12);

            LoanResponse response = loanService.applyForLoan(
                    request, testUser.getEmail());

            // Reducing balance EMI for ₹1L, 12%, 12 months ≈ ₹8,884.88
            assertThat(response.getEmiAmount())
                    .isBetween(
                            new BigDecimal("8883.00"),
                            new BigDecimal("8886.00"));
        }

        @Test
        @DisplayName("Total payable should always be greater than principal")
        void emi_totalPayableExceedsPrincipal() {
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(any(), any()))
                    .thenReturn(testAccount);
            when(loanRepository.existsByUserAndStatusIn(any(), any()))
                    .thenReturn(false);
            when(loanRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            LoanApplicationRequest request = buildRequest(
                    LoanType.HOME_LOAN,
                    new BigDecimal("1000000"),
                    120);

            LoanResponse response = loanService.applyForLoan(
                    request, testUser.getEmail());

            assertThat(response.getTotalPayableAmount())
                    .isGreaterThan(new BigDecimal("1000000"));
        }

        @Test
        @DisplayName("emisRemaining should equal tenure on new application")
        void emi_emisRemainingEqualsTenureOnApplication() {
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(any(), any()))
                    .thenReturn(testAccount);
            when(loanRepository.existsByUserAndStatusIn(any(), any()))
                    .thenReturn(false);
            when(loanRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            LoanApplicationRequest request = buildRequest(
                    LoanType.CAR_LOAN,
                    new BigDecimal("500000"),
                    36);

            LoanResponse response = loanService.applyForLoan(
                    request, testUser.getEmail());

            assertThat(response.getEmisPaid()).isZero();
            assertThat(response.getEmisRemaining()).isEqualTo(36);
        }
    }

    @Nested
    @DisplayName("Loan Application Validation Tests")
    class ApplicationValidationTests {

        @Test
        @DisplayName("Should reject personal loan below minimum amount")
        void apply_personalLoanBelowMinimum() {
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(any(), any()))
                    .thenReturn(testAccount);


            LoanApplicationRequest request = buildRequest(
                    LoanType.PERSONAL_LOAN,
                    new BigDecimal("5000"), // below ₹10,000 minimum
                    12);

            assertThatThrownBy(() ->
                    loanService.applyForLoan(
                            request, testUser.getEmail()))
                    .isInstanceOf(AccountOperationException.class)
                    .hasMessageContaining("₹10,000");
        }

        @Test
        @DisplayName("Should reject when user already has active loan")
        void apply_rejectWhenActiveLoanExists() {
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(any(), any()))
                    .thenReturn(testAccount);
            when(loanRepository.existsByUserAndStatusIn(any(), any()))
                    .thenReturn(true); // active loan exists

            LoanApplicationRequest request = buildRequest(
                    LoanType.PERSONAL_LOAN,
                    new BigDecimal("50000"),
                    12);

            assertThatThrownBy(() ->
                    loanService.applyForLoan(
                            request, testUser.getEmail()))
                    .isInstanceOf(AccountOperationException.class)
                    .hasMessageContaining("active or pending loan");
        }

        @Test
        @DisplayName("Should reject home loan below minimum tenure")
        void apply_homeLoanTenureTooShort() {
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(accountService.getOwnedAccount(any(), any()))
                    .thenReturn(testAccount);


            LoanApplicationRequest request = buildRequest(
                    LoanType.HOME_LOAN,
                    new BigDecimal("1000000"),
                    6); // below 12-month minimum for home loan

            assertThatThrownBy(() ->
                    loanService.applyForLoan(
                            request, testUser.getEmail()))
                    .isInstanceOf(AccountOperationException.class)
                    .hasMessageContaining("12–240 months");
        }
    }

    @Nested
    @DisplayName("Loan Approval and Rejection Tests")
    class ReviewTests {

        @Test
        @DisplayName("Should reject approving a non-PENDING loan")
        void approve_nonPendingLoan() {
            Loan activeLoan = buildLoan(LoanStatus.ACTIVE);

            when(loanRepository.findById(1L))
                    .thenReturn(Optional.of(activeLoan));
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));

            LoanReviewRequest review = new LoanReviewRequest();
            review.setReason("looks good");

            assertThatThrownBy(() ->
                    loanService.approveLoan(
                            1L, review, testUser.getEmail()))
                    .isInstanceOf(AccountOperationException.class)
                    .hasMessageContaining("Only PENDING loans");
        }

        @Test
        @DisplayName("Should set status to REJECTED on rejection")
        void reject_setsStatusCorrectly() {
            Loan pendingLoan = buildLoan(LoanStatus.PENDING);

            when(loanRepository.findById(1L))
                    .thenReturn(Optional.of(pendingLoan));
            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(loanRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            LoanReviewRequest review = new LoanReviewRequest();
            review.setReason("Credit score too low");

            LoanResponse response = loanService.rejectLoan(
                    1L, review, testUser.getEmail());

            assertThat(response.getStatus())
                    .isEqualTo(LoanStatus.REJECTED);
            assertThat(response.getRejectionReason())
                    .isEqualTo("Credit score too low");
        }
    }

    @Nested
    @DisplayName("EMI Payment Tests")
    class EmiPaymentTests {

        @Test
        @DisplayName("Should throw InsufficientFunds when balance " +
                "below EMI amount")
        void payEmi_insufficientBalance() {
            Loan activeLoan = buildLoan(LoanStatus.ACTIVE);
            activeLoan.setEmiAmount(new BigDecimal("8885.00"));

            testAccount.setBalance(new BigDecimal("1000.00")); // less than EMI

            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(loanRepository.findById(1L))
                    .thenReturn(Optional.of(activeLoan));

            assertThatThrownBy(() ->
                    loanService.payEmi(1L, testUser.getEmail()))
                    .isInstanceOf(InsufficientFundsException.class)
                    .hasMessageContaining("Insufficient balance");
        }

        @Test
        @DisplayName("Loan should close when all EMIs are paid")
        void payEmi_loanClosesOnFinalPayment() {
            Loan activeLoan = buildLoan(LoanStatus.ACTIVE);
            activeLoan.setTenureMonths(12);
            activeLoan.setEmisPaid(11); // one left
            activeLoan.setEmiAmount(new BigDecimal("8885.00"));
            activeLoan.setOutstandingAmount(new BigDecimal("8885.00"));
            activeLoan.setNextEmiDate(java.time.LocalDate.now());

            testAccount.setBalance(new BigDecimal("50000.00"));

            when(userRepository.findByEmail(any()))
                    .thenReturn(Optional.of(testUser));
            when(loanRepository.findById(1L))
                    .thenReturn(Optional.of(activeLoan));
            when(accountRepository.save(any()))
                    .thenReturn(testAccount);
            when(emiPaymentRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(loanRepository.save(any()))
                    .thenAnswer(inv -> inv.getArgument(0));

            LoanResponse response = loanService.payEmi(
                    1L, testUser.getEmail());

            assertThat(response.getStatus())
                    .isEqualTo(LoanStatus.CLOSED);
            assertThat(response.getOutstandingAmount())
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

    private LoanApplicationRequest buildRequest(
            LoanType type, BigDecimal amount, int tenure) {
        LoanApplicationRequest request = new LoanApplicationRequest();
        request.setLoanType(type);
        request.setAmount(amount);
        request.setTenureMonths(tenure);
        request.setPurpose("Test purpose");
        request.setAccountId(1L);
        return request;
    }

    private Loan buildLoan(LoanStatus status) {
        Loan loan = new Loan();
        loan.setId(1L);
        loan.setLoanRef("LOANTEST123");
        loan.setUser(testUser);
        loan.setAccount(testAccount);
        loan.setLoanType(LoanType.PERSONAL_LOAN);
        loan.setStatus(status);
        loan.setPrincipalAmount(new BigDecimal("100000"));
        loan.setInterestRate(new BigDecimal("12.00"));
        loan.setTenureMonths(12);
        loan.setEmiAmount(new BigDecimal("8885.00"));
        loan.setOutstandingAmount(new BigDecimal("100000"));
        loan.setEmisPaid(0);
        loan.setNextEmiDate(java.time.LocalDate.now().plusMonths(1));
        return loan;
    }
}