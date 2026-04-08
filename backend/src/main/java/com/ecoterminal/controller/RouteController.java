package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.RouteResponse;
import com.ecoterminal.model.dto.ZoneOccupancyResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.RouteService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/routes")
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    /** GET /api/routes/suggest — kullanıcının yaklaşan uçuşuna rota öner */
    @GetMapping("/suggest")
    public ResponseEntity<ApiResponse<RouteResponse>> suggestRoute(
            @AuthenticationPrincipal UserPrincipal principal) {
        RouteResponse route = routeService.getSuggestedRoute(principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(route));
    }

    /** GET /api/routes/alternatives/{zoneId} — sakin bekleme alanları */
    @GetMapping("/alternatives/{zoneId}")
    public ResponseEntity<ApiResponse<List<ZoneOccupancyResponse>>> getAlternatives(
            @PathVariable Long zoneId) {
        List<ZoneOccupancyResponse> alts = routeService.getQuietAlternatives(zoneId);
        return ResponseEntity.ok(ApiResponse.ok(alts));
    }
}
