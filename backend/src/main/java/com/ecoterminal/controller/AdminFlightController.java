package com.ecoterminal.controller;

import com.ecoterminal.model.dto.*;
import com.ecoterminal.service.FlightService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/flights")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Flights", description = "Uçuş CRUD yönetimi")
public class AdminFlightController {

    private final FlightService flightService;

    /** GET /api/admin/flights — tüm uçuşlar (admin detayı) */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AdminFlightResponse>>> getAllFlights() {
        return ResponseEntity.ok(ApiResponse.ok(flightService.getAllFlightsForAdmin()));
    }

    /** POST /api/admin/flights — yeni uçuş oluştur */
    @PostMapping
    public ResponseEntity<ApiResponse<AdminFlightResponse>> createFlight(
            @Valid @RequestBody AdminFlightRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(flightService.createFlight(req)));
    }

    /** PUT /api/admin/flights/{id} — uçuş güncelle */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<AdminFlightResponse>> updateFlight(
            @PathVariable Long id,
            @Valid @RequestBody AdminFlightRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(flightService.updateFlight(id, req)));
    }

    /** DELETE /api/admin/flights/{id} — uçuş sil */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFlight(@PathVariable Long id) {
        flightService.deleteFlight(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** GET /api/admin/flights/airlines — havayolu listesi */
    @GetMapping("/airlines")
    public ResponseEntity<ApiResponse<List<AirlineResponse>>> getAirlines() {
        return ResponseEntity.ok(ApiResponse.ok(flightService.getAllAirlines()));
    }

    /** GET /api/admin/flights/gates — kapı (GATE tipi zone) listesi */
    @GetMapping("/gates")
    public ResponseEntity<ApiResponse<List<FlightService.ZoneResponse>>> getGates() {
        return ResponseEntity.ok(ApiResponse.ok(flightService.getGateZones()));
    }
}
