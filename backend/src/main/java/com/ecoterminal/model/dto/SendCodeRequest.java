package com.ecoterminal.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendCodeRequest(
        @NotBlank(message = "E-posta boş olamaz")
        @Email(message = "Geçerli bir e-posta girin")
        String email,

        @NotBlank(message = "Ad soyad boş olamaz")
        @Size(min = 2, max = 100)
        String fullName,

        @NotBlank(message = "Şifre boş olamaz")
        @Size(min = 6, max = 100)
        String password
) {}
