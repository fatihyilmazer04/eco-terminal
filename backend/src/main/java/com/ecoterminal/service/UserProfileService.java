package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.model.entity.UserProfile;
import com.ecoterminal.repository.EcoWalletRepository;
import com.ecoterminal.repository.UserProfileRepository;
import com.ecoterminal.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserRepository        userRepository;
    private final UserProfileRepository profileRepository;
    private final EcoWalletRepository   walletRepository;
    private final ObjectMapper          objectMapper;

    // ── Profil Getir ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı"));
        UserProfile profile = profileRepository.findByUserUserId(userId).orElse(null);

        WalletResponse wallet = walletRepository.findByUser_UserId(userId)
                .map(WalletResponse::from)
                .orElse(null);

        PreferencesResponse prefs = parsePreferences(profile);

        return new ProfileResponse(
                user.getUserId(),
                user.getEmail(),
                profile != null ? profile.getFullName() : null,
                profile != null ? profile.getPhone() : null,
                profile != null ? profile.getAvatarUrl() : null,
                user.getRole().name(),
                wallet,
                prefs
        );
    }

    // ── Profil Güncelle ───────────────────────────────────────────────────

    @Transactional
    public ProfileResponse updateProfile(Long userId, UpdateProfileRequest req) {
        UserProfile profile = profileRepository.findByUserUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı profili"));

        if (req.fullName()  != null) profile.setFullName(req.fullName());
        if (req.phone()     != null) profile.setPhone(req.phone());
        if (req.avatarUrl() != null) profile.setAvatarUrl(req.avatarUrl());

        profileRepository.save(profile);
        log.info("Profil güncellendi: userId={}", userId);
        return getProfile(userId);
    }

    // ── Tercih Güncelle ───────────────────────────────────────────────────

    @Transactional
    public ProfileResponse updatePreferences(Long userId, PreferencesRequest req) {
        UserProfile profile = profileRepository.findByUserUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı profili"));

        // Mevcut JSON'ı oku, üzerine yaz
        Map<String, Object> prefs = readPrefsMap(profile.getPreferencesJson());

        if (req.seatPreference()    != null) prefs.put("seatPreference",    req.seatPreference());
        if (req.mealPreference()    != null) prefs.put("mealPreference",    req.mealPreference());
        if (req.crowdAlerts()       != null) prefs.put("crowdAlerts",       req.crowdAlerts());
        if (req.flightUpdates()     != null) prefs.put("flightUpdates",     req.flightUpdates());
        if (req.routeSuggestions()  != null) prefs.put("routeSuggestions",  req.routeSuggestions());
        if (req.ecoRewards()        != null) prefs.put("ecoRewards",        req.ecoRewards());

        try {
            profile.setPreferencesJson(objectMapper.writeValueAsString(prefs));
        } catch (Exception e) {
            throw new BusinessException("Tercihler kaydedilemedi", org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR);
        }

        profileRepository.save(profile);
        log.debug("Tercihler güncellendi: userId={}", userId);
        return getProfile(userId);
    }

    // ── Yardımcı ─────────────────────────────────────────────────────────

    private PreferencesResponse parsePreferences(UserProfile profile) {
        if (profile == null || profile.getPreferencesJson() == null) {
            return PreferencesResponse.defaults();
        }
        Map<String, Object> map = readPrefsMap(profile.getPreferencesJson());
        return new PreferencesResponse(
                str(map, "seatPreference", "NO_PREFERENCE"),
                str(map, "mealPreference", "STANDARD"),
                bool(map, "crowdAlerts",      true),
                bool(map, "flightUpdates",    true),
                bool(map, "routeSuggestions", true),
                bool(map, "ecoRewards",       true)
        );
    }

    private Map<String, Object> readPrefsMap(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String str(Map<String, Object> m, String key, String def) {
        Object v = m.get(key);
        return v instanceof String s ? s : def;
    }

    private boolean bool(Map<String, Object> m, String key, boolean def) {
        Object v = m.get(key);
        return v instanceof Boolean b ? b : def;
    }
}
