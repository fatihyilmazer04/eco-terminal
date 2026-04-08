package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.FlightDetailResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.FlightService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/flights")
@RequiredArgsConstructor
@Tag(name = "Flights", description = "Yolcu uçuş bilgileri ve bilet sorgulama")
public class FlightController {

    private final FlightService flightService;

    /** GET /api/flights/my — kullanıcının kendi aktif biletleri */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<FlightDetailResponse>>> getMyFlights(
            @AuthenticationPrincipal UserPrincipal principal) {
        List<FlightDetailResponse> flights = flightService.getMyFlights(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(flights));
    }

    /** GET /api/flights — ADMIN: tüm uçuşlar */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<FlightDetailResponse>>> getAllFlights() {
        List<FlightDetailResponse> flights = flightService.getAllFlights();
        return ResponseEntity.ok(ApiResponse.ok(flights));
    }

    /** GET /api/flights/{id} — uçuş detayı */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FlightDetailResponse>> getFlightDetails(
            @PathVariable Long id) {
        FlightDetailResponse detail = flightService.getFlightDetails(id);
        return ResponseEntity.ok(ApiResponse.ok(detail));
    }
}
