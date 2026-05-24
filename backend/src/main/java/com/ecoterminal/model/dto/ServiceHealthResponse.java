package com.ecoterminal.model.dto;

/**
 * Servis sağlık durumu — her bileşen için döner.
 */
public record ServiceHealthResponse(
        String  name,
        String  status,      // UP | DOWN | UNKNOWN
        long    responseMs,
        String  detail
) {
    public static ServiceHealthResponse up(String name, long ms) {
        return new ServiceHealthResponse(name, "UP", ms, null);
    }

    public static ServiceHealthResponse down(String name, String reason) {
        return new ServiceHealthResponse(name, "DOWN", -1, reason);
    }
}
