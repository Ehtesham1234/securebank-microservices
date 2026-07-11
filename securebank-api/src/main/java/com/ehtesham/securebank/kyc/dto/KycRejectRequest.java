package com.ehtesham.securebank.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KycRejectRequest {

    @NotBlank(message = "Rejection reason is required")
    private String reason;
}