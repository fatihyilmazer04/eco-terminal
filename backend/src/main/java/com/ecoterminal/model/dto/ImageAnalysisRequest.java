package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/zones/{zoneId}/analyze-image — istek gövdesi.
 * image_base64: data URI prefix'i ile veya saf base64 ikisi de kabul edilir.
 */
public record ImageAnalysisRequest(

        @NotBlank(message = "image_base64 boş olamaz")
        @Size(max = 15_000_000, message = "Görüntü çok büyük (max ~10 MB)")
        String image_base64
) {}
