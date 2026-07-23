package com.ehtesham.loan_service.controller;

import com.ehtesham.loan_service.dto.*;
import com.ehtesham.loan_service.dto.response.ApiResponse;
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
    public ResponseEntity<ApiResponse<LoanResponse>> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String userEmail) {

        LoanResponse response = loanService.applyForLoan(request, userId, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Loan application submitted", response, 201));
    }

    @GetMapping("/loans/my")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getMyLoans(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<LoanResponse> loans = loanService.getMyLoans(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Fetched user loans", loans));
    }

    @GetMapping("/loans/{id}")
    public ResponseEntity<ApiResponse<LoanResponse>> getLoan(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", defaultValue = "ROLE_CUSTOMER") String role) {

        boolean isStaff = role.contains("ADMIN") || role.contains("TELLER");
        LoanResponse loan = loanService.getLoanDetails(id, userId, isStaff);
        return ResponseEntity.ok(ApiResponse.success("Fetched loan details", loan));
    }

    @PostMapping("/loans/{id}/approve")
    public ResponseEntity<ApiResponse<LoanResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        LoanResponse loan = loanService.approveLoan(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Loan approved successfully", loan));
    }

    @PostMapping("/loans/{id}/reject")
    public ResponseEntity<ApiResponse<LoanResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            @RequestHeader("X-User-Id") Long userId) {

        LoanResponse loan = loanService.rejectLoan(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Loan rejected successfully", loan));
    }

    @PostMapping("/loans/{id}/pay-emi")
    public ResponseEntity<ApiResponse<LoanResponse>> payEmi(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Account-Id") Long accountId) {

        LoanResponse loan = loanService.payEmi(id, userId, accountId);
        return ResponseEntity.ok(ApiResponse.success("EMI payment successful", loan));
    }

    @GetMapping("/admin/loans")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getAllLoans(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        Page<LoanResponse> loans = loanService.getAllLoans(pageable);
        return ResponseEntity.ok(ApiResponse.success("Fetched all loans", loans));
    }

    @GetMapping("/admin/loans/status/{status}")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getByStatus(
            @PathVariable String status,
            @PageableDefault(size = 20) Pageable pageable) {

        Page<LoanResponse> loans = loanService.getLoansByStatus(status, pageable);
        return ResponseEntity.ok(ApiResponse.success("Fetched loans by status", loans));
    }
}
