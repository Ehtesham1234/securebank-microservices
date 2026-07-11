package com.ehtesham.securebank.user.dto;

import com.ehtesham.securebank.common.enums.Role;
import com.ehtesham.securebank.common.enums.UserStatus;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private Role role;
    private UserStatus userStatus;
    private boolean emailVerified;
}