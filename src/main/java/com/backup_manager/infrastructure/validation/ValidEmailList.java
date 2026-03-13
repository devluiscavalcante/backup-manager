package com.backup_manager.infrastructure.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = EmailListValidator.class)
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidEmailList {

    String message() default "Lista contém emails inválidos";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}