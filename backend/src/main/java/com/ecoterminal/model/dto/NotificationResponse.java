package com.ecoterminal.model.dto;

import com.ecoterminal.model.entity.Notification;
import com.ecoterminal.model.entity.NotificationType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public record NotificationResponse(
        Long notifId,
        String title,
        String body,
        NotificationType type,
        Boolean isRead,
        String zoneName,
        Instant createdAt,
        String timeAgo
) {
    public static NotificationResponse from(Notification n) {
        String zoneName = (n.getZone() != null) ? n.getZone().getZoneName() : null;
        return new NotificationResponse(
                n.getNotifId(),
                n.getTitle(),
                n.getBody(),
                n.getType(),
                n.getIsRead(),
                zoneName,
                n.getCreatedAt(),
                computeTimeAgo(n.getCreatedAt())
        );
    }

    private static String computeTimeAgo(Instant createdAt) {
        if (createdAt == null) return "";
        long seconds = ChronoUnit.SECONDS.between(createdAt, Instant.now());
        if (seconds < 60)      return seconds + " saniye önce";
        long minutes = seconds / 60;
        if (minutes < 60)      return minutes + " dakika önce";
        long hours = minutes / 60;
        if (hours < 24)        return hours + " saat önce";
        long days = hours / 24;
        return days + " gün önce";
    }
}
