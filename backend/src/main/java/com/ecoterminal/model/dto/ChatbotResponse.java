package com.ecoterminal.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatbotResponse(
        String reply,
        List<String> suggestedZones,
        Object data
) {
    public static ChatbotResponse of(String reply) {
        return new ChatbotResponse(reply, null, null);
    }

    public static ChatbotResponse withZones(String reply, List<String> zones) {
        return new ChatbotResponse(reply, zones, null);
    }

    public static ChatbotResponse withData(String reply, List<String> zones, Object data) {
        return new ChatbotResponse(reply, zones, data);
    }
}
