package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Uçuş bilgisi.
 * gate: ManyToOne → Zone (GATE tipi bölge)
 * departure_time: DB'de TIMESTAMP WITH TIME ZONE → Java'da Instant
 */
@Entity
@Table(
    name = "flights",
    indexes = {
        @Index(name = "idx_flights_departure", columnList = "departure_time"),
        @Index(name = "idx_flights_status",    columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Flight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "flight_id")
    private Long flightId;

    @Column(name = "flight_code", nullable = false, length = 20)
    private String flightCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "airline_id")
    private Airline airline;

    @Column(name = "destination", nullable = false, length = 100)
    private String destination;

    @Column(name = "origin", length = 100)
    private String origin;

    @Column(name = "departure_time", nullable = false)
    private Instant departureTime;

    @Column(name = "arrival_time")
    private Instant arrivalTime;

    /**
     * gate bağlantısı Zone tablosuna ManyToOne.
     * Uçuş iptal veya gate değişirse null olabilir.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gate_id")
    private Zone gate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private FlightStatus status = FlightStatus.SCHEDULED;
}
