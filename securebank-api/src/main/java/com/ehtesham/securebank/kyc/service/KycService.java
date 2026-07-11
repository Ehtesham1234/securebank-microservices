package com.ehtesham.securebank.kyc.service;

import com.ehtesham.securebank.kyc.dto.KycRejectRequest;
import com.ehtesham.securebank.kyc.dto.KycResponse;
import com.ehtesham.securebank.kyc.dto.KycSubmitRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KycService {

    // CUSTOMER
    KycResponse submitKyc(KycSubmitRequest request,
                          MultipartFile file,
                          String email);

    KycResponse getMyKycStatus(String email);

    // TELLER
    List<KycResponse> getPendingKycList();

    KycResponse verifyKyc(Long kycId, String tellerEmail);

    KycResponse rejectKyc(Long kycId,
                          KycRejectRequest request,
                          String tellerEmail);
}