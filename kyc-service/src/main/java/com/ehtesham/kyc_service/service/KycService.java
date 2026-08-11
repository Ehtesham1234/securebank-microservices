package com.ehtesham.kyc_service.service;

import com.ehtesham.kyc_service.dto.KycDocumentFile;
import com.ehtesham.kyc_service.dto.KycRejectRequest;
import com.ehtesham.kyc_service.dto.KycResponse;
import com.ehtesham.kyc_service.dto.KycSubmitRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KycService {

    // userId from SecurityContext — no email param
    KycResponse submitKyc(KycSubmitRequest request,
                          MultipartFile file);

    KycResponse getMyKycStatus();

    List<KycResponse> getPendingKycList();

    // C7 fix: lets a teller/admin actually see the uploaded document
    // before verifying/rejecting it, or the submitting customer see
    // their own. Enforces that ownership/staff check itself (same
    // "self or staff, 404 not 403 otherwise" pattern used elsewhere) —
    // userId comes from SecurityContext, not a caller-supplied param.
    KycDocumentFile getKycDocumentFile(Long kycId);

    // tellerUserId from SecurityContext
    KycResponse verifyKyc(Long kycId);

    KycResponse rejectKyc(Long kycId,
                          KycRejectRequest request);
}