package com.ehtesham.account_service.card.controller;

import com.ehtesham.account_service.card.dto.CardResponse;
import com.ehtesham.account_service.card.dto.CreditCardRequest;
import com.ehtesham.account_service.card.dto.CvvResponse;
import com.ehtesham.account_service.card.dto.StatementResponse;
import com.ehtesham.account_service.card.dto.VerifyCvvRequest;
import com.ehtesham.account_service.card.dto.VerifyCvvResponse;
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
// H1 fix: @Validated is required for @RequestParam/@PathVariable
// constraints (like @DecimalMin below) to be enforced at all — without
// it, Spring silently ignores them on method parameters.
@org.springframework.validation.annotation.Validated
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
            @RequestParam @jakarta.validation.constraints.DecimalMin(
                    value = "0.01", message = "Amount must be positive") BigDecimal amount,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.ok(ApiResponse.success(
                "Credit card bill paid successfully",
                cardService.payCreditCardBill(cardId, amount, idempotencyKey)
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
            @RequestParam @jakarta.validation.constraints.DecimalMin(
                    value = "0.01", message = "Amount must be positive") BigDecimal amount,
            @RequestParam(required = false) String description) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card spend recorded",
                cardService.spend(cardId, amount, description)
        ));
    }

    // Follow-up #5 (Option A): CVV is derived on demand, never stored —
    // see CvvService. Self-service only: both endpoints require the
    // caller to own the card (same as every other /cards/{cardId}/**
    // endpoint above). A future third-party/merchant "pay with card"
    // flow (PAN + CVV only, no cardholder session) would be a different
    // trust model entirely and isn't what these expose.
    @GetMapping("/{cardId}/cvv")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<CvvResponse>> revealCvv(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "CVV retrieved",
                cardService.revealCvv(cardId)
        ));
    }

    @PostMapping("/{cardId}/verify-cvv")
    @PreAuthorize("hasAuthority('ROLE_CUSTOMER')")
    public ResponseEntity<ApiResponse<VerifyCvvResponse>> verifyCvv(
            @PathVariable Long cardId,
            @Valid @RequestBody VerifyCvvRequest request) {
        boolean valid = cardService.verifyCvv(cardId, request.getCvv());
        return ResponseEntity.ok(ApiResponse.success(
                valid ? "CVV verified" : "CVV does not match",
                VerifyCvvResponse.builder().valid(valid).build()
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


    @GetMapping("/admin")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<List<CardResponse>>> getAllCards(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long cardId,
            @RequestParam(required = false) String maskedNumber,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(ApiResponse.success(
                "Fetched all cards",
                cardService.getAllCards(userId, cardId, maskedNumber, search)
        ));
    }

    @PostMapping("/admin/{cardId}/block")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CardResponse>> adminBlockCard(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card blocked successfully",
                cardService.adminBlockCard(cardId)
        ));
    }

    @PostMapping("/admin/{cardId}/unblock")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<CardResponse>> adminUnblockCard(
            @PathVariable Long cardId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Card unblocked successfully",
                cardService.adminUnblockCard(cardId)
        ));
    }
}
