package com.ehtesham.loan_service.controller;

import com.ehtesham.loan_service.dto.*;
import com.ehtesham.loan_service.service.LoanService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @PostMapping("/loans/apply")
    public ResponseEntity<LoanResponse> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(loanService.applyForLoan(
                        request, userId, userEmail));
    }

    @GetMapping("/loans/my")
    public ResponseEntity<Page<LoanResponse>> getMyLoans(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(
                loanService.getMyLoans(userId, pageable));
    }

    @GetMapping("/loans/{id}")
    public ResponseEntity<LoanResponse> getLoan(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role",
                    defaultValue = "ROLE_CUSTOMER") String role) {

        boolean isStaff = role.contains("ADMIN")
                || role.contains("TELLER");
        return ResponseEntity.ok(
                loanService.getLoanDetails(id, userId, isStaff));
    }

    @PostMapping("/loans/{id}/approve")
    public ResponseEntity<LoanResponse> approve(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                loanService.approveLoan(id, request, userId));
    }

    @PostMapping("/loans/{id}/reject")
    public ResponseEntity<LoanResponse> reject(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        return ResponseEntity.ok(
                loanService.rejectLoan(id, request, userId));
    }

    @PostMapping("/loans/{id}/pay-emi")
    public ResponseEntity<LoanResponse> payEmi(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Account-Id") Long accountId) {

        return ResponseEntity.ok(
                loanService.payEmi(id, userId, accountId));
    }

    @GetMapping("/admin/loans")
    public ResponseEntity<Page<LoanResponse>> getAllLoans(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(loanService.getAllLoans(pageable));
    }

    @GetMapping("/admin/loans/status/{status}")
    public ResponseEntity<Page<LoanResponse>> getByStatus(
            @PathVariable String status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
                loanService.getLoansByStatus(status, pageable));
    }
}