package com.ehtesham.kyc_service.dto;


import com.ehtesham.kyc_service.enums.KycDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KycSubmitRequest {

    @NotNull(message = "Document type is required")
    private KycDocumentType documentType;

    @NotBlank(message = "Document number is required")
    private String documentNumber;

    // file handled separately via MultipartFile
    // not in this DTO
}