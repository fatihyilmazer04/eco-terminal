package com.ecoterminal.repository;

import com.ecoterminal.model.entity.Flight;
import com.ecoterminal.model.entity.FlightStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface FlightRepository extends JpaRepository<Flight, Long> {

    List<Flight> findByStatus(FlightStatus status);

    List<Flight> findByStatusOrderByDepartureTimeAsc(FlightStatus status);

    /** Belirli bir tarihten sonra kalkan ve iptal olmayan uçuşlar */
    @Query("""
            SELECT f FROM Flight f
            WHERE f.departureTime >= :from
              AND f.status <> 'CANCELLED'
            ORDER BY f.departureTime ASC
            """)
    List<Flight> findUpcomingFlights(@Param("from") Instant from);
}
