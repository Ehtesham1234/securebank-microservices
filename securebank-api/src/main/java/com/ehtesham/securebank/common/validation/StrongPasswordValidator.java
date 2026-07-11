package com.ehtesham.securebank.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class StrongPasswordValidator
        implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(
            String password, ConstraintValidatorContext context) {

        if (password == null || password.length() < 8) {
            addError(context, "Password must be at least 8 characters");
            return false;
        }

        boolean hasUppercase = password.chars().anyMatch(Character::isUpperCase);
        boolean hasLowercase = password.chars().anyMatch(Character::isLowerCase);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        boolean hasSpecialChar = password.chars()
                .anyMatch(ch -> "@$!%*?&#^()_+-=[]{}".indexOf(ch) >= 0);

        if (!hasUppercase) {
            addError(context, "Password must contain at least one uppercase letter");
            return false;
        }

        if (!hasLowercase) {
            addError(context, "Password must contain at least one lowercase letter");
            return false;
        }

        if (!hasDigit) {
            addError(context, "Password must contain at least one digit");
            return false;
        }

        if (!hasSpecialChar) {
            addError(context, "Password must contain at least one special character (@$!%*?&#^()_+-=[]{})");
            return false;
        }

        return true;
    }

    private void addError(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message)
                .addConstraintViolation();
    }
}