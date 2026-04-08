package com.ecoterminal.repository;

import com.ecoterminal.model.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {

    /** Kullanıcının tüm biletleri */
    List<Ticket> findByUser_UserId(Long userId);

    /** Kullanıcının sadece aktif biletleri */
    List<Ticket> findByUser_UserIdAndIsActiveTrue(Long userId);

    /**
     * Kullanıcının aktif biletleri + uçuş + kapı bilgisi tek sorguda.
     * FETCH JOIN ile N+1 önlenir.
     */
    @Query("""
            SELECT t FROM Ticket t
            JOIN FETCH t.flight f
            LEFT JOIN FETCH f.airline
            LEFT JOIN FETCH f.gate
            WHERE t.user.userId = :userId
              AND t.isActive = true
            ORDER BY f.departureTime ASC
            """)
    List<Ticket> findActiveTicketsWithFlight(@Param("userId") Long userId);

    /**
     * Belirli bir bölgede (gate) aktif bileti olan kullanıcıları bul.
     * triggerCrowdAlert için yoğunluk bildiriminin gönderileceği kullanıcılar.
     */
    @Query("""
            SELECT DISTINCT t FROM Ticket t
            JOIN FETCH t.user u
            WHERE t.flight.gate.zoneId = :zoneId
              AND t.isActive = true
            """)
    List<Ticket> findActiveTicketsByGateZoneId(@Param("zoneId") Long zoneId);
}
