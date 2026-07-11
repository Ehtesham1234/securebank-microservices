package com.ehtesham.securebank.kyc.dto;

import com.ehtesham.securebank.common.enums.KycDocumentType;
import com.ehtesham.securebank.common.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycResponse {

    private Long id;
    private Long userId;
    private String userEmail;
    private KycDocumentType documentType;
    private String documentNumber;
    private KycStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}