package com.ehtesham.securebank.user.dto;

import lombok.*;

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