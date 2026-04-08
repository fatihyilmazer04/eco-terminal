package com.ecoterminal.service;

import com.ecoterminal.exception.BusinessException;
import com.ecoterminal.model.entity.UserProfile;
import com.ecoterminal.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserProfileRepository profileRepository;

    /**
     * Kullanıcının FCM token'ını günceller.
     * Uygulama her açılışta yeni token alıp bu endpoint'e gönderir.
     */
    @Transactional
    public void updateFcmToken(Long userId, String fcmToken) {
        UserProfile profile = profileRepository.findByUserUserId(userId)
                .orElseThrow(() -> BusinessException.notFound("Kullanıcı profili"));
        profile.setFcmToken(fcmToken);
        profileRepository.save(profile);
        log.debug("FCM token güncellendi: userId={}", userId);
    }
}
