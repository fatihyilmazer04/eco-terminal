package com.ecoterminal.controller;

import com.ecoterminal.model.dto.ApiResponse;
import com.ecoterminal.model.dto.FcmTokenRequest;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class FcmTokenController {

    private final UserService userService;

    /** PUT /api/users/fcm-token — cihazın FCM token'ını kaydet/güncelle */
    @PutMapping("/fcm-token")
    public ResponseEntity<ApiResponse<Void>> updateFcmToken(
            @RequestBody @Valid FcmTokenRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        userService.updateFcmToken(principal.getUserId(), request.fcmToken());
        return ResponseEntity.ok(ApiResponse.ok(null));
    }
}
