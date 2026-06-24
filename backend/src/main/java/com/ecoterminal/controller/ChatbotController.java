package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ChatbotRequest;
import com.ecoterminal.model.dto.ChatbotResponse;
import com.ecoterminal.model.dto.ProviderInfoResponse;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.ChatbotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * LLM tabanlı chatbot endpoint'leri.
 *
 * POST /api/chatbot/ask      → soru gönder (provider opsiyonel: "cloud" | "local")
 * GET  /api/chatbot/providers → mevcut sağlayıcılar ve durumları
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "Yolcu asistan chatbot (LLM tabanlı)")
public class ChatbotController {

    private final ChatbotService chatbotService;

    /**
     * POST /api/chatbot/ask
     * Body: { "message": "Gate A1 dolu mu?", "provider": "cloud" }
     * provider alanı opsiyoneldir; verilmezse uygulama default'u kullanılır.
     */
    @PostMapping("/api/chatbot/ask")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Chatbot'a soru sor",
               description = "RAG ile zenginleştirilmiş LLM yanıtı döner. provider: 'cloud' veya 'local'")
    public ResponseEntity<ApiResponse<ChatbotResponse>> ask(
            @Valid @RequestBody ChatbotRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.debug("Chatbot isteği: userId={} provider='{}' mesaj='{}'",
                principal.getUserId(), request.provider(), request.message());

        ChatbotResponse response = chatbotService.ask(
                request.message(), request.provider(), principal.getUserId());

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/chatbot/providers
     * Frontend ayar ekranının provider listesini çekmesi için kullanılır.
     */
    @GetMapping("/api/chatbot/providers")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Mevcut chatbot sağlayıcılarını listele")
    public ResponseEntity<ApiResponse<List<ProviderInfoResponse>>> getProviders() {
        return ResponseEntity.ok(ApiResponse.ok(chatbotService.getProviders()));
    }
}
