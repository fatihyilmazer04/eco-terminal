package com.ecoterminal.model.dto;

/**
 * Sistem bileşenlerinin anlık sağlık durumu.
 * Her alan "UP" | "DOWN" | "UNKNOWN" değeri taşır.
 */
public record SystemHealthResponse(
        String backend,
        String database,
        String redis,
        String aiService,
        String yolov8
) {}
