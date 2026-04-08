package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "tickets",
    indexes = {
        @Index(name = "idx_tickets_user",   columnList = "user_id"),
        @Index(name = "idx_tickets_flight", columnList = "flight_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ticket_id")
    private Long ticketId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * EAGER fetch: bilet okunduğunda uçuş bilgisi de gerekli (tek sorgu).
     * N+1 yerine JOIN ile getirilir.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    @Column(name = "seat_number", length = 10)
    private String seatNumber;

    /**
     * "class" Java keyword'ü olduğu için alan adı seatClass,
     * DB sütun adı "class" olarak eşleniyor.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "class", nullable = false, length = 20)
    @Builder.Default
    private SeatClass seatClass = SeatClass.ECONOMY;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "check_in_time")
    private Instant checkInTime;

    @CreationTimestamp
    @Column(name = "booked_at", nullable = false, updatable = false)
    private Instant bookedAt;
}
