package com.backup_manager.infrastructure.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.List;
import java.util.regex.Pattern;

public class EmailListValidator implements ConstraintValidator<ValidEmailList, List<String>> {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    );

    @Override
    public boolean isValid(List<String> emails, ConstraintValidatorContext context) {
        if (emails == null || emails.isEmpty()) {
            return true;
        }

        for (String email : emails) {
            if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
                return false;
            }
        }

        return true;
    }
}