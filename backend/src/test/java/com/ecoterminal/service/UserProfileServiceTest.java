package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.dto.*;
import com.ecoterminal.model.entity.*;
import com.ecoterminal.repository.EcoWalletRepository;
import com.ecoterminal.repository.UserProfileRepository;
import com.ecoterminal.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserProfileService Unit Tests")
class UserProfileServiceTest {

    @Mock private UserRepository        userRepository;
    @Mock private UserProfileRepository profileRepository;
    @Mock private EcoWalletRepository   walletRepository;

    @InjectMocks
    private UserProfileService userProfileService;

    // ObjectMapper gerçek instance — JSON parse/write için
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User testUser;
    private UserProfile testProfile;
    private EcoWallet testWallet;

    @BeforeEach
    void setUp() {
        // Gerçek ObjectMapper'ı inject et
        try {
            var field = UserProfileService.class.getDeclaredField("objectMapper");
            field.setAccessible(true);
            field.set(userProfileService, objectMapper);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        testUser = User.builder()
                .userId(1L)
                .email("test@eco.com")
                .role(Role.USER)
                .isActive(true)
                .build();

        testProfile = UserProfile.builder()
                .profileId(1L)
                .user(testUser)
                .fullName("Test Kullanıcı")
                .phone("05001234567")
                .avatarUrl("https://example.com/avatar.png")
                .build();

        testWallet = EcoWallet.builder()
                .walletId(1L)
                .user(testUser)
                .currentBalance(100)
                .tierLevel(TierLevel.GREEN)
                .build();
    }

    // ── getProfile Tests ──────────────────────────────────────────────────

    @Test
    @DisplayName("getProfile_withValidUser_returnsProfileResponse")
    void getProfile_withValidUser_returnsProfileResponse() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(walletRepository.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));

        // when
        ProfileResponse response = userProfileService.getProfile(1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.email()).isEqualTo("test@eco.com");
        assertThat(response.fullName()).isEqualTo("Test Kullanıcı");
        assertThat(response.phone()).isEqualTo("05001234567");
        assertThat(response.role()).isEqualTo("USER");
        assertThat(response.wallet()).isNotNull();
        assertThat(response.wallet().currentBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("getProfile_withNoProfile_returnsNullFields")
    void getProfile_withNoProfile_returnsNullFields() {
        // given
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.empty());
        when(walletRepository.findByUser_UserId(1L)).thenReturn(Optional.empty());

        // when
        ProfileResponse response = userProfileService.getProfile(1L);

        // then
        assertThat(response).isNotNull();
        assertThat(response.fullName()).isNull();
        assertThat(response.wallet()).isNull();
    }

    @Test
    @DisplayName("getProfile_withNonExistentUser_throwsNotFound")
    void getProfile_withNonExistentUser_throwsNotFound() {
        // given
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userProfileService.getProfile(99L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── updateProfile Tests ───────────────────────────────────────────────

    @Test
    @DisplayName("updateProfile_withValidRequest_updatesFields")
    void updateProfile_withValidRequest_updatesFields() {
        // given
        UpdateProfileRequest req = new UpdateProfileRequest("Yeni Ad", "05559876543", null);
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));

        // when
        ProfileResponse response = userProfileService.updateProfile(1L, req);

        // then
        assertThat(testProfile.getFullName()).isEqualTo("Yeni Ad");
        assertThat(testProfile.getPhone()).isEqualTo("05559876543");
        verify(profileRepository).save(testProfile);
    }

    @Test
    @DisplayName("updateProfile_withNullFields_doesNotOverwrite")
    void updateProfile_withNullFields_doesNotOverwrite() {
        // given — tüm alanlar null, mevcut değerler korunmalı
        UpdateProfileRequest req = new UpdateProfileRequest(null, null, null);
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));

        // when
        userProfileService.updateProfile(1L, req);

        // then — değerler değişmemiş olmalı
        assertThat(testProfile.getFullName()).isEqualTo("Test Kullanıcı");
        assertThat(testProfile.getPhone()).isEqualTo("05001234567");
    }

    @Test
    @DisplayName("updateProfile_withNonExistentProfile_throwsNotFound")
    void updateProfile_withNonExistentProfile_throwsNotFound() {
        // given
        when(profileRepository.findByUserUserId(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userProfileService.updateProfile(99L,
                new UpdateProfileRequest("Ad", null, null)))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── updatePreferences Tests ───────────────────────────────────────────

    @Test
    @DisplayName("updatePreferences_withValidRequest_savesJsonPreferences")
    void updatePreferences_withValidRequest_savesJsonPreferences() {
        // given
        PreferencesRequest req = new PreferencesRequest("WINDOW", "VEGAN", true, false, true, true);
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));

        // when
        userProfileService.updatePreferences(1L, req);

        // then — JSON kaydedilmiş olmalı
        assertThat(testProfile.getPreferencesJson()).contains("WINDOW");
        assertThat(testProfile.getPreferencesJson()).contains("VEGAN");
        verify(profileRepository).save(testProfile);
    }

    @Test
    @DisplayName("updatePreferences_withNullFields_keepsExistingValues")
    void updatePreferences_withNullFields_keepsExistingValues() throws Exception {
        // given — mevcut json'da seatPreference var
        testProfile.setPreferencesJson("{\"seatPreference\":\"AISLE\",\"crowdAlerts\":true}");
        PreferencesRequest req = new PreferencesRequest(null, null, false, null, null, null);
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(walletRepository.findByUser_UserId(1L)).thenReturn(Optional.of(testWallet));

        // when
        userProfileService.updatePreferences(1L, req);

        // then — seatPreference korunmalı, crowdAlerts güncellenmeli
        assertThat(testProfile.getPreferencesJson()).contains("AISLE");
        assertThat(testProfile.getPreferencesJson()).contains("\"crowdAlerts\":false");
    }
}
