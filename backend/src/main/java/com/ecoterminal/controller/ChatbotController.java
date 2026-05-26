package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.ChatbotRequest;
import com.ecoterminal.model.dto.ChatbotResponse;
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

/**
 * Kural tabanlı chatbot endpoint'i.
 * Harici LLM kullanmaz — keyword eşleştirme + DB sorguları.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Chatbot", description = "Yolcu asistan chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;

    /**
     * POST /api/chatbot/ask
     * Body: { "message": "En sakin lounge nerede?" }
     */
    @PostMapping("/api/chatbot/ask")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @Operation(summary = "Chatbot'a soru sor", description = "Kural tabanlı niyet analizi ile DB'den yanıt döner")
    public ResponseEntity<ApiResponse<ChatbotResponse>> ask(
            @Valid @RequestBody ChatbotRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        log.debug("Chatbot isteği: userId={} mesaj='{}'", principal.getUserId(), request.message());

        ChatbotResponse response = chatbotService.ask(request.message(), principal.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
