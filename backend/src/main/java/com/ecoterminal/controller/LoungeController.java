package com.ecoterminal.controller;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.LoungeResponse;
import com.ecoterminal.service.LoungeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/lounges")
@RequiredArgsConstructor
public class LoungeController {

    private final LoungeService loungeService;

    /** GET /api/lounges — density < 0.50 olan LOUNGE bölgeleri */
    @GetMapping
    public ResponseEntity<ApiResponse<List<LoungeResponse>>> getQuietLounges() {
        return ResponseEntity.ok(ApiResponse.ok(loungeService.getQuietLounges()));
    }

    /** GET /api/lounges/best — en düşük doluluklu LOUNGE */
    @GetMapping("/best")
    public ResponseEntity<ApiResponse<LoungeResponse>> getBestLounge() {
        LoungeResponse best = loungeService.getBestLounge()
                .orElseThrow(() -> new BusinessException("Aktif lounge bulunamadı", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.ok(best));
    }
}
