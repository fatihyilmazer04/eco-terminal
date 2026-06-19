package com.ecoterminal.controller;

import com.ecoterminal.model.dto.AdminTicketRequest;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.TicketDetailResponse;
import com.ecoterminal.service.TicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/tickets")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin — Tickets", description = "Bilet yönetimi (admin)")
public class AdminTicketController {

    private final TicketService ticketService;

    /** GET /api/admin/tickets — tüm biletler */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TicketDetailResponse>>> getAllTickets() {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.getAllTickets()));
    }

    /** POST /api/admin/tickets — yeni bilet + PNR üret */
    @PostMapping
    public ResponseEntity<ApiResponse<TicketDetailResponse>> createTicket(
            @Valid @RequestBody AdminTicketRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.createTicket(req)));
    }

    /** DELETE /api/admin/tickets/{id} — bilet sil */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteTicket(@PathVariable Long id) {
        ticketService.deleteTicket(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
