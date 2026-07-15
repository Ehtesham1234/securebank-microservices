package com.ehtesham.loan_service.service;

import com.ehtesham.loan_service.dto.LoanApplicationRequest;
import com.ehtesham.loan_service.dto.LoanResponse;
import com.ehtesham.loan_service.dto.LoanReviewRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LoanService {
    public LoanResponse applyForLoan(
            LoanApplicationRequest request,
            Long userId, String userEmail);
    public LoanResponse approveLoan(Long loanId,
                                    LoanReviewRequest request, Long reviewerUserId);
    public LoanResponse rejectLoan(Long loanId,
                                   LoanReviewRequest request, Long reviewerUserId);
    public LoanResponse payEmi(Long loanId, Long userId,
                               Long accountId);
    public Page<LoanResponse> getMyLoans(
            Long userId, Pageable pageable);
    public LoanResponse getLoanDetails(Long loanId, Long userId,
                                       boolean isStaff);
    public Page<LoanResponse> getAllLoans(Pageable pageable);
    public Page<LoanResponse> getLoansByStatus(
            String status, Pageable pageable);
    public void activateLoan(Long loanId, String transactionRef);
    public void failLoan(Long loanId, String reason);
}
