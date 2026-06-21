package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.entity.User;
import com.ecoterminal.model.entity.UserProfile;
import com.ecoterminal.model.entity.Role;
import com.ecoterminal.repository.UserProfileRepository;
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
@DisplayName("UserService Unit Tests")
class UserServiceTest {

    @Mock private UserProfileRepository profileRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;
    private UserProfile testProfile;

    @BeforeEach
    void setUp() {
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
                .fcmToken("old-token")
                .build();
    }

    @Test
    @DisplayName("updateFcmToken_withValidUser_updatesSavedToken")
    void updateFcmToken_withValidUser_updatesSavedToken() {
        // given
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.save(any(UserProfile.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        userService.updateFcmToken(1L, "new-fcm-token-abc123");

        // then
        assertThat(testProfile.getFcmToken()).isEqualTo("new-fcm-token-abc123");
        verify(profileRepository).save(testProfile);
    }

    @Test
    @DisplayName("updateFcmToken_withNonExistentUser_throwsNotFound")
    void updateFcmToken_withNonExistentUser_throwsNotFound() {
        // given
        when(profileRepository.findByUserUserId(99L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> userService.updateFcmToken(99L, "some-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException be = (BusinessException) ex;
                    assertThat(be.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        verify(profileRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateFcmToken_callsSaveExactlyOnce")
    void updateFcmToken_callsSaveExactlyOnce() {
        // given
        when(profileRepository.findByUserUserId(1L)).thenReturn(Optional.of(testProfile));
        when(profileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        userService.updateFcmToken(1L, "token-xyz");

        // then
        verify(profileRepository, times(1)).save(any(UserProfile.class));
    }
}
