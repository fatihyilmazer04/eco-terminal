package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.PnrClaimRequest;
import com.ecoterminal.model.dto.TicketDetailResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.TicketService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
@Tag(name = "Tickets", description = "PNR bilet sorgulama ve claim")
public class TicketController {

    private final TicketService ticketService;

    /**
     * GET /api/tickets/lookup?pnrCode=TK-A3F2B1
     * PNR önizleme — claim etmeden bilet bilgisini göster.
     */
    @GetMapping("/lookup")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> lookupPnr(
            @RequestParam String pnrCode) {
        return ResponseEntity.ok(ApiResponse.ok(ticketService.lookupByPnr(pnrCode)));
    }

    /**
     * POST /api/tickets/claim
     * PNR ile bileti mevcut kullanıcının hesabına bağla.
     */
    @PostMapping("/claim")
    public ResponseEntity<ApiResponse<TicketDetailResponse>> claimTicket(
            @Valid @RequestBody PnrClaimRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                ticketService.claimTicket(req.pnrCode(), principal.getUserId())));
    }

    /**
     * POST /api/tickets/{ticketId}/unclaim
     * Bileti kullanıcının hesabından kaldır (user_id = NULL). Bilet silinmez.
     */
    @PostMapping("/{ticketId}/unclaim")
    public ResponseEntity<ApiResponse<Void>> unclaimTicket(
            @PathVariable Long ticketId,
            @AuthenticationPrincipal UserPrincipal principal) {
        ticketService.unclaimTicket(ticketId, principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
