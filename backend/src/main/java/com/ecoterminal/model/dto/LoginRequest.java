package com.ecoterminal.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 6, message = "Şifre en az 6 karakter olmalıdır")
        String password
) {}
