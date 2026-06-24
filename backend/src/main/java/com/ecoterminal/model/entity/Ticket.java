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
        @Index(name = "idx_tickets_flight", columnList = "flight_id"),
        @Index(name = "idx_tickets_pnr",    columnList = "pnr_code")
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

    /**
     * nullable = true: claim edilmemiş biletler user'sız olabilir.
     * Kullanıcı PNR ile bileti claim edince bu alan set edilir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "flight_id", nullable = false)
    private Flight flight;

    /**
     * PNR kodu — admin tarafından üretilir, yolcu bu kodla bileti claim eder.
     * Format: "TK-A3F2B1", "PC-X7K2M9"
     */
    @Column(name = "pnr_code", unique = true, length = 10)
    private String pnrCode;

    /** Bilet sahibinin adı (admin girişi, claim sonrası zorunlu değil). */
    @Column(name = "passenger_name", length = 100)
    private String passengerName;

    /** Bilet durumu: ACTIVE, CANCELLED */
    @Column(name = "ticket_status", nullable = false, length = 20)
    @Builder.Default
    private String ticketStatus = "ACTIVE";

    @Column(name = "seat_number", length = 10)
    private String seatNumber;

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
