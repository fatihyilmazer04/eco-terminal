package com.ecoterminal.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 6, max = 100, message = "Şifre 6-100 karakter arasında olmalıdır")
        String password,

        @NotBlank(message = "Ad soyad boş olamaz")
        @Size(min = 2, max = 100, message = "Ad soyad 2-100 karakter arasında olmalıdır")
        String fullName
) {}
