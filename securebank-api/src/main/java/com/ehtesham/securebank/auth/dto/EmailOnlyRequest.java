package com.ehtesham.securebank.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmailOnlyRequest {

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;
}