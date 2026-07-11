package com.ehtesham.securebank.loan.service;

import com.ehtesham.securebank.loan.dto.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoanService {

    LoanResponse applyForLoan(
            LoanApplicationRequest request, String email);

    LoanResponse approveLoan(
            Long loanId, LoanReviewRequest request, String reviewerEmail);

    LoanResponse rejectLoan(
            Long loanId, LoanReviewRequest request, String reviewerEmail);

    LoanResponse payEmi(Long loanId, String email);

    Page<LoanResponse> getMyLoans(String email, Pageable pageable);

    LoanResponse getLoanDetails(Long loanId, String email);

    Page<LoanResponse> getAllLoans(Pageable pageable);

    Page<LoanResponse> getLoansByStatus(
            String status, Pageable pageable);
}