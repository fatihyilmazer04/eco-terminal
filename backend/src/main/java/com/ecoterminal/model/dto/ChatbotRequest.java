package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatbotRequest(
        @NotBlank(message = "Mesaj boş olamaz")
        @Size(max = 500, message = "Mesaj en fazla 500 karakter olabilir")
        String message
) {}
