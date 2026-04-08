package com.ecoterminal.service;

import com.ecoterminal.model.dto.AdminDashboardResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.FlightStatus;
import com.ecoterminal.repository.FlightRepository;
import com.ecoterminal.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final OccupancyService occupancyService;
    private final EnergyService    energyService;
    private final FlightRepository flightRepository;
    private final TicketRepository ticketRepository;

    /**
     * Admin ana ekranı için sistem geneli özet.
     * Toplam 4 sorgu: occupancy, energy, flights, savings.
     */
    @Transactional(readOnly = true)
    public AdminDashboardResponse getSummary() {
        List<ZoneOccupancyResponse> zones = occupancyService.getAllZonesWithOccupancy();

        // totalPassengers: tüm bölgelerdeki anlık toplam kişi sayısı
        int totalPassengers = zones.stream()
                .mapToInt(z -> z.currentCount() != null ? z.currentCount() : 0)
                .sum();

        // criticalZoneCount: density >= 0.85 (HIGH veya CRITICAL)
        int criticalZoneCount = (int) zones.stream()
                .filter(z -> z.densityLevel() == DensityLevel.HIGH
                          || z.densityLevel() == DensityLevel.CRITICAL)
                .count();

        // averageDensityPct
        float averageDensityPct = (float) zones.stream()
                .filter(z -> z.densityPct() != null)
                .mapToDouble(ZoneOccupancyResponse::densityPct)
                .average()
                .orElse(0.0);

        // totalEnergyKwh
        float totalEnergyKwh = energyService.getTotalEnergyKwh();

        // activeFlightCount: SCHEDULED + BOARDING
        int activeFlightCount =
                flightRepository.findByStatus(FlightStatus.SCHEDULED).size()
              + flightRepository.findByStatus(FlightStatus.BOARDING).size();

        // savingSuggestionCount: WASTEFUL bölge sayısı
        int savingSuggestionCount = energyService.getSavingSuggestions().size();

        log.debug("Admin summary: {} passengers, {} critical zones, {} active flights, {} saving suggestions",
                totalPassengers, criticalZoneCount, activeFlightCount, savingSuggestionCount);

        return new AdminDashboardResponse(
                totalPassengers,
                criticalZoneCount,
                averageDensityPct,
                totalEnergyKwh,
                activeFlightCount,
                savingSuggestionCount,
                zones
        );
    }
}
