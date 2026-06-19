package com.ecoterminal.model.dto;

public record ProviderInfoResponse(
        String  name,
        String  displayName,
        boolean available
) {}
