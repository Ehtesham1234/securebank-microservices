package com.ehtesham.securebank.auth.dto;

import com.ehtesham.securebank.common.enums.Role;
import com.ehtesham.securebank.common.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private UserStatus userStatus;
    private Role role;
}