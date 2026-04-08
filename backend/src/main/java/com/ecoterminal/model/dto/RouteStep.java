package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.DensityLevel;

/**
 * Rota önerisinin tek adımı.
 * densityLevel: bu adımın geçtiği bölgenin anlık doluluk seviyesi.
 */
public record RouteStep(
        int stepNumber,
        String zoneName,
        String instruction,
        int estimatedWalkMinutes,
        DensityLevel densityLevel
) {}
