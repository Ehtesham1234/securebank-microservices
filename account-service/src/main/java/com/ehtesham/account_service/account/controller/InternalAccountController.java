package com.ehtesham.account_service.account.controller;

import com.ehtesham.account_service.account.dto.AccountResponse;
import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import com.ehtesham.account_service.transaction.dto.EmiDebitResponse;
import com.ehtesham.account_service.transaction.service.impl.MoneyMovementExecutor;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Called service-to-service by kyc-service/loan-service on behalf of a
 * real authenticated request (a teller verifying KYC, a customer
 * applying for a loan) — the caller's JWT is forwarded and verified the
 * same way as any other request (see GatewayAuthFilter). Authorization
 * for WHO may call each endpoint is enforced below via @PreAuthorize,
 * same as any other controller.
 */
@RestController
@RequestMapping("/api/v1/internal")
@Validated
public class InternalAccountController {

    private final AccountService accountService;
    private final CardService cardService;
    private final AccountRepository accountRepository;
    private final MoneyMovementExecutor moneyMovementExecutor;

    public InternalAccountController(
            AccountService accountService,
            CardService cardService,
            AccountRepository accountRepository,
            MoneyMovementExecutor moneyMovementExecutor) {
        this.accountService = accountService;
        this.cardService = cardService;
        this.accountRepository = accountRepository;
        this.moneyMovementExecutor = moneyMovementExecutor;
    }

    /**
     * Called by kyc-service after KYC verification. Acts on a DIFFERENT
     * user than the caller (the teller creates infrastructure for the
     * customer) — restricted to staff roles.
     */
    @PostMapping("/accounts/kyc-setup")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN')")
    public ResponseEntity<AccountResponse> kycSetup(
            @RequestParam Long userId,
            @RequestParam String firstName,
            @RequestParam String lastName) {

        // Create savings account
        AccountResponse accountResponse =
                accountService.createSavingsAccount(
                        userId, firstName, lastName);

        // Load the account entity for card creation
        Account account = accountRepository
                .findById(accountResponse.getId())
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Failed to load created account"));

        // Create debit card automatically
        String cardHolderName = firstName + " " + lastName;
        cardService.createDebitCard(
                userId, cardHolderName, account);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(accountResponse);
    }

    /**
     * Called by loan-service to validate account ownership before
     * approving a loan. Triggered by the loan applicant's OWN request —
     * so this allows the caller to check THEIR OWN account, or staff
     * checking on anyone's behalf.
     */
    @GetMapping("/accounts/{accountId}/validate")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN') or #userId == authentication.details")
    public ResponseEntity<com.ehtesham.account_service.account.dto.AccountValidationResponse>
    validateAccount(
            @PathVariable Long accountId,
            @RequestParam Long userId) {

        return ResponseEntity.ok(
                accountService.validateAccount(
                        accountId, userId));
    }

    /**
     * C4 fix: called synchronously by loan-service's payEmi(), BEFORE it
     * changes any of its own loan/EMI records — so a failed debit here
     * (insufficient funds, frozen account, etc.) means the loan side
     * never gets modified either. Triggered by the loan applicant's own
     * request, same self-or-staff rule as validateAccount above.
     */
    @PostMapping("/accounts/{accountId}/debit-for-emi")
    @PreAuthorize("hasAnyAuthority('ROLE_TELLER','ROLE_ADMIN') or #userId == authentication.details")
    public ResponseEntity<EmiDebitResponse> debitForEmi(
            @PathVariable Long accountId,
            @RequestParam Long userId,
            @RequestParam Long loanId,
            @RequestParam Integer emiNumber,
            @RequestParam @DecimalMin(value = "0.01", message = "Amount must be positive") BigDecimal amount,
            @RequestParam(required = false) String description) {

        EmiDebitResponse response = moneyMovementExecutor.doEmiDebit(
                accountId, userId, loanId, emiNumber, amount,
                description != null ? description
                        : "EMI payment for loan " + loanId);

        return ResponseEntity.ok(response);
    }
}