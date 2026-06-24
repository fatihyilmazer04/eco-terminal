package com.ecoterminal.service.chatbot;

import java.util.List;

/**
 * RAG bağlamı — kullanıcının sorusundan önce DB'den çekilip
 * LLM sistem promptuna enjekte edilen gerçek zamanlı veriler.
 */
public record ChatContext(
        List<FlightInfo>  userFlights,
        Integer           ecoPoints,
        String            tierLevel,
        List<ZoneInfo>    hotZones,
        List<ZoneInfo>    quietZones,
        int               avgDensityPct,
        String            currentTime,
        long              unreadNotificationCount
) {

    public record FlightInfo(
            String code,
            String destination,
            String gate,
            String departureTime,
            String status
    ) {}

    public record ZoneInfo(
            String name,
            String type,
            int    densityPct
    ) {}
}
