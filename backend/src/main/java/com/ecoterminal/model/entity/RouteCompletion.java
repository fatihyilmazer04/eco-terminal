package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "route_completions")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RouteCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "flight_id", nullable = false)
    private Long flightId;

    @Column(name = "points_earned", nullable = false)
    @Builder.Default
    private int pointsEarned = 50;

    @CreationTimestamp
    @Column(name = "completed_at", nullable = false, updatable = false)
    private Instant completedAt;
}
