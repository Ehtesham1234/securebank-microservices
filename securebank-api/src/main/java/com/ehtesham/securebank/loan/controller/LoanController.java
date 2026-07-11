package com.ehtesham.securebank.loan.controller;

import com.ehtesham.securebank.common.response.ApiResponse;
import com.ehtesham.securebank.loan.dto.*;
import com.ehtesham.securebank.loan.service.LoanService;
import com.ehtesham.securebank.security.service.CustomUserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loans", description = "Loan application and EMI management")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    // CUSTOMER — apply for loan
    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<LoanResponse>> apply(
            @Valid @RequestBody LoanApplicationRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        LoanResponse response = loanService.applyForLoan(
                request, principal.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(
                        "Loan application submitted", response));
    }

    // CUSTOMER — view my loans
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getMyLoans(
            @PageableDefault(size = 10, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Loans retrieved",
                loanService.getMyLoans(
                        principal.getUsername(), pageable)));
    }

    // CUSTOMER — view loan details + pay EMI
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<LoanResponse>> getLoan(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Loan retrieved",
                loanService.getLoanDetails(
                        id, principal.getUsername())));
    }

    @PostMapping("/{id}/pay-emi")
    public ResponseEntity<ApiResponse<LoanResponse>> payEmi(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "EMI paid successfully",
                loanService.payEmi(id, principal.getUsername())));
    }

    // TELLER + ADMIN — review pending loans
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<LoanResponse>> approve(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Loan approved",
                loanService.approveLoan(
                        id, request, principal.getUsername())));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<LoanResponse>> reject(
            @PathVariable Long id,
            @Valid @RequestBody LoanReviewRequest request,
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        return ResponseEntity.ok(ApiResponse.success(
                "Loan rejected",
                loanService.rejectLoan(
                        id, request, principal.getUsername())));
    }

    // ADMIN — view all loans
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getAll(
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                "All loans retrieved",
                loanService.getAllLoans(pageable)));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<ApiResponse<Page<LoanResponse>>> getByStatus(
            @PathVariable String status,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.success(
                "Loans retrieved",
                loanService.getLoansByStatus(status, pageable)));
    }
}