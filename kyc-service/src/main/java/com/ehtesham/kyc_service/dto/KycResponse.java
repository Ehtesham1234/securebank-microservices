package com.ehtesham.kyc_service.dto;

import com.ehtesham.kyc_service.enums.KycDocumentType;
import com.ehtesham.kyc_service.enums.KycStatus;
import lombok.*;
import java.time.LocalDateTime;

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class KycResponse {
    private Long id;
    private Long userId;
    private KycDocumentType documentType;
    private String documentNumber;
    private KycStatus status;
    private String rejectionReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}