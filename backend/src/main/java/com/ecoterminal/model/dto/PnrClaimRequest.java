package com.ecoterminal.model.dto;

import jakarta.validation.constraints.NotBlank;

public record PnrClaimRequest(@NotBlank String pnrCode) {}
