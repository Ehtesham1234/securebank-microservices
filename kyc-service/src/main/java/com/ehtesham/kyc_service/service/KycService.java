package com.ehtesham.kyc_service.service;

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

    // tellerUserId from SecurityContext
    KycResponse verifyKyc(Long kycId);

    KycResponse rejectKyc(Long kycId,
                          KycRejectRequest request);
}