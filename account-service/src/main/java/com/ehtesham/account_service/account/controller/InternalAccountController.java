package com.ehtesham.account_service.account.controller;

import com.ehtesham.account_service.account.dto.AccountResponse;
import com.ehtesham.account_service.account.entity.Account;
import com.ehtesham.account_service.account.repository.AccountRepository;
import com.ehtesham.account_service.account.service.AccountService;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Called by kyc-service after KYC verification.
     * Creates savings account + debit card atomically.
     */
    @PostMapping("/accounts/kyc-setup")
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
     * Called by loan-service to validate account ownership
     * before approving a loan.
     */
    @GetMapping("/accounts/{accountId}/validate")
    public ResponseEntity<com.ehtesham.account_service.account.dto.AccountValidationResponse>
    validateAccount(
            @PathVariable Long accountId,
            @RequestParam Long userId) {

        return ResponseEntity.ok(
                accountService.validateAccount(
                        accountId, userId));
    }
}