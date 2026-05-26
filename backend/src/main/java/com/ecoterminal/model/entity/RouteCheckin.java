package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "route_checkins", indexes = {
        @Index(name = "idx_route_checkin_unique",      columnList = "user_id, flight_id, step_number", unique = true),
        @Index(name = "idx_route_checkin_user_flight", columnList = "user_id, flight_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteCheckin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "step_number", nullable = false)
    private Integer stepNumber;

    @Column(name = "zone_name", nullable = false, length = 100)
    private String zoneName;

    @Column(name = "total_steps", nullable = false)
    private Integer totalSteps;

    @CreationTimestamp
    @Column(name = "checked_in_at", nullable = false, updatable = false)
    private Instant checkedInAt;
}
