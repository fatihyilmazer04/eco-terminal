package com.ecoterminal.controller;

import com.ecoterminal.model.dto.*;
import com.ecoterminal.security.UserPrincipal;
import com.ecoterminal.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    /** GET /api/users/profile — profil + cüzdan + tercihler */
    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(profileService.getProfile(principal.getUserId())));
    }

    /** PUT /api/users/profile — isim, telefon, avatar güncelle */
    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updateProfile(principal.getUserId(), request)));
    }

    /** PUT /api/users/preferences — bildirim ve uçuş tercihleri */
    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<ProfileResponse>> updatePreferences(
            @RequestBody PreferencesRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(
                profileService.updatePreferences(principal.getUserId(), request)));
    }
}
