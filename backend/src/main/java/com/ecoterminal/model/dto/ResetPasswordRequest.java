package com.ecoterminal.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        String email,

        @NotBlank(message = "Doğrulama kodu boş olamaz")
        @Size(min = 6, max = 6, message = "Doğrulama kodu 6 haneli olmalıdır")
        String code,

        @NotBlank(message = "Yeni şifre boş olamaz")
        @Size(min = 6, max = 100, message = "Şifre en az 6 karakter olmalıdır")
        String newPassword
) {}
