package com.ehtesham.kyc_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
@JsonIgnoreProperties(ignoreUnknown = true)
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class InternalUserResponse {
    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String role;
    private String userStatus;
}