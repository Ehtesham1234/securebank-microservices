package com.ehtesham.account_service.account.controller;

import com.ehtesham.account_service.account.dto.AccountResponse;
import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
public class InternalAccountController {

    private final AccountService accountService;
    private final CardService cardService;
    private final AccountRepository accountRepository;

    public InternalAccountController(
            AccountService accountService,
            CardService cardService,
            AccountRepository accountRepository) {
        this.accountService = accountService;
        this.cardService = cardService;
        this.accountRepository = accountRepository;
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
}