package com.ehtesham.securebank.auth.dto;

import com.ehtesham.securebank.common.validation.StrongPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest {

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
    @StrongPassword
    @NotBlank(message = "Password is required")
    private String password;

//    @NotBlank(message = "Phone number is required")
//    @Pattern(
//            regexp = "^[0-9]{10}$",
//            message = "Phone number must be 10 digits"
//    )
//    private String phoneNumber;
}