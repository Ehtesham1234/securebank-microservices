package com.ehtesham.account_service.card.controller;

import com.ehtesham.account_service.card.dto.CardResponse;
import com.ehtesham.account_service.card.dto.CreditCardRequest;
import com.ehtesham.account_service.card.dto.StatementResponse;
import com.ehtesham.account_service.card.service.CardService;
import com.ehtesham.account_service.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards",
        description = "Debit and credit card management")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<List<CardResponse>>> getMyCards() {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched customer cards",
                cardService.getMyCards()
        ));
    }

    @PostMapping("/{cardId}/block")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<CardResponse>> blockCard(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card blocked successfully",
                cardService.blockCard(cardId)
        ));
    }

    @PostMapping("/{cardId}/unblock")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<CardResponse>> unblockCard(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card unblocked successfully",
                cardService.unblockCard(cardId)
        ));
    }

    @PostMapping("/{cardId}/pay-bill")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<CardResponse>> payCreditCardBill(
            @PathVariable Long cardId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(ApiResponse.success(
                "Credit card bill paid successfully",
                cardService.payCreditCardBill(cardId, amount)
        ));
    }

    @GetMapping("/{cardId}/statements")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<List<StatementResponse>>> getStatements(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched card statements",
                cardService.getStatements(cardId)
        ));
    }

    @PostMapping("/{cardId}/spend")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<CardResponse>> spend(
            @PathVariable Long cardId,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card spend recorded",
                cardService.spend(cardId, amount, description)
        ));
    }

    // Admin endpoints
    @PostMapping("/admin/issue-credit")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CardResponse>> issueCreditCard(
            @Valid @RequestBody CreditCardRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                "Credit card issued successfully",
                cardService.issueCreditCard(request)
        ));
    }

    @PostMapping("/admin/{cardId}/cancel")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CardResponse>> cancelCard(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card cancelled successfully",
                cardService.cancelCard(cardId)
        ));
    }
}
