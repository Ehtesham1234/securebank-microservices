package com.ehtesham.securebank.card.controller;

import com.ehtesham.securebank.card.dto.*;
import com.ehtesham.securebank.card.service.CardService;
import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "Debit and credit card management")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CardResponse>>> getMyCards(
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Cards retrieved",
                cardService.getMyCards(principal.getUsername())));
    }

    @PostMapping("/{id}/block")
    public ResponseEntity<ApiResponse<CardResponse>> block(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card blocked",
                cardService.blockCard(id, principal.getUsername())));
    }

    @PostMapping("/{id}/unblock")
    public ResponseEntity<ApiResponse<CardResponse>> unblock(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card unblocked",
                cardService.unblockCard(
                        id, principal.getUsername())));
    }

    @PostMapping("/{id}/pay-bill")
    public ResponseEntity<ApiResponse<CardResponse>> payBill(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Bill payment successful",
                cardService.payCreditCardBill(
                        id, amount, principal.getUsername())));
    }
    @GetMapping("/{id}/statements")
    public ResponseEntity<ApiResponse<List<StatementResponse>>> statements(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Statements retrieved",
                cardService.getStatements(
                        id, principal.getUsername())));
    }
    // CardController:
    @PostMapping("/{id}/spend")
    public ResponseEntity<ApiResponse<CardResponse>> spend(
            @PathVariable Long id,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Spend recorded",
                cardService.spend(id, amount, description,
                        principal.getUsername())));
    }

    // Admin endpoints
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/credit/issue")
    public ResponseEntity<ApiResponse<CardResponse>> issueCreditCard(
            @Valid @RequestBody CreditCardRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Credit card issued",
                cardService.issueCreditCard(
                        request, principal.getUsername())));
    }
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<CardResponse>> cancel(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card cancelled",
                cardService.cancelCard(
                        id, principal.getUsername())));
    }
}