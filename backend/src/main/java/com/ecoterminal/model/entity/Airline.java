package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "airlines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Airline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "airline_id")
    private Long airlineId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "iata_code", nullable = false, unique = true, length = 3)
    private String iataCode;
}
