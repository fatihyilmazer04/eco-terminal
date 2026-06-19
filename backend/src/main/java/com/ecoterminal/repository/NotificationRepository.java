package com.ecoterminal.repository;

import com.ecoterminal.model.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** En yeni 50 bildirim — sayfalı, tüm listeyi çekmez */
    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /** Geriye dönük uyumluluk için limitsiz — iç kullanım */
    List<Notification> findByUser_UserIdOrderByCreatedAtDesc(Long userId);

    List<Notification> findByUser_UserIdAndIsReadFalse(Long userId);

    long countByUser_UserIdAndIsReadFalse(Long userId);

    List<Notification> findByUser_UserIdAndCreatedAtAfter(Long userId, Instant after);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.userId = :userId AND n.isRead = false")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.user.userId = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);
}
