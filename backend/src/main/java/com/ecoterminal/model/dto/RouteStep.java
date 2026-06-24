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
        DensityLevel densityLevel,
        Double posX,   // nullable — SVG merkez X% (posX + width/2)
        Double posY    // nullable — SVG merkez Y% (posY + height/2)
) {}
