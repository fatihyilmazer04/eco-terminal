package com.ecoterminal.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatbotResponse(
        String             reply,
        List<String>       suggestedZones,
        Object             data,
        String             provider,      // hangi modelin cevap verdiği ("cloud" / "local" / "llm-service")
        // LLM servisinden gelen ek metadata (null olabilir)
        String             intent,
        Double             confidence,
        List<RouteStepInfo> routeSteps,
        List<String>       sourcesUsed
) {
    /** Adım bazlı rota bilgisi (llm-service tarafından üretilir). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record RouteStepInfo(
            int    stepNumber,
            String zoneName,
            String instruction,
            int    estimatedWalkMinutes
    ) {}

    public static ChatbotResponse of(String reply, String provider) {
        return new ChatbotResponse(reply, null, null, provider, null, null, null, null);
    }

    public static ChatbotResponse withZones(String reply, List<String> zones, String provider) {
        return new ChatbotResponse(reply, zones, null, provider, null, null, null, null);
    }

    public static ChatbotResponse withData(String reply, List<String> zones, Object data, String provider) {
        return new ChatbotResponse(reply, zones, data, provider, null, null, null, null);
    }

    public static ChatbotResponse rich(String reply, String provider,
                                       String intent, Double confidence,
                                       List<RouteStepInfo> routeSteps, List<String> sourcesUsed) {
        return new ChatbotResponse(reply, null, null, provider, intent, confidence, routeSteps, sourcesUsed);
    }
}
