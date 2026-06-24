# Requirements Traceability Matrix (RTM)
## Eco-Terminal — Akıllı Havalimanı Yoğunluk ve Enerji Yönetim Sistemi

> **Test Ortamı:** JUnit 5 + Mockito (Unit), Spring Boot Test + Testcontainers (Integration)  
> **CI/CD:** GitHub Actions — Workflow: `Eco-Terminal CI` — Son Çalıştırma: ✅ **Success** (6m 37s)  
> **Toplam Test Sayısı:** 222 Unit + Integration Test

---

## FR-001 — FR-006 | KİMLİK DOĞRULAMA & GÜVENLİK

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-001 | Geçerli kimlik bilgileriyle login yapılınca JWT access+refresh token üretilmeli | Auth | ✅ Tamamlandı | Unit + Integration | `AuthServiceTest` → `login_withValidCredentials_returnsAuthResponse`<br>`AuthControllerIntegrationTest` → `loginEndpoint_withValidCredentials_returns200AndToken` | ✅ PASS |
| FR-002 | Geçersiz şifre ile login girişiminde `BadCredentials` exception fırlatılmalı | Auth | ✅ Tamamlandı | Unit | `AuthServiceTest` → `login_withInvalidPassword_throwsException`<br>`AuthControllerIntegrationTest` → `loginEndpoint_withInvalidCredentials_returns401` | ✅ PASS |
| FR-003 | Yeni kullanıcı kaydı tamamlandığında kullanıcı ve profil kaydı oluşturulmalı | Auth | ✅ Tamamlandı | Unit + Integration | `AuthServiceTest` → `register_withValidData_createsUserAndProfile`<br>`AuthControllerIntegrationTest` → `registerEndpoint_withValidData_returns200` | ✅ PASS |
| FR-004 | Aynı e-posta ile tekrar kayıt yapılmaya çalışılınca HTTP 409 dönmeli | Auth | ✅ Tamamlandı | Unit + Integration | `AuthServiceTest` → `register_withExistingEmail_throwsException`<br>`AuthControllerIntegrationTest` → `registerEndpoint_withDuplicateEmail_returns409` | ✅ PASS |
| FR-005 | JWT token doğrulama: geçerli/geçersiz/süresi dolmuş/kurcalanmış token senaryoları | Auth (JWT) | ✅ Tamamlandı | Unit | `JwtServiceTest` → `validateAccessToken_withValidToken_returnsTrue`<br>`validateToken_withExpiredToken_returnsFalse`<br>`validateToken_withTamperedToken_returnsFalse`<br>`validateAccessToken_withRefreshToken_returnsFalse` | ✅ PASS |
| FR-006 | Token olmadan korumalı endpoint'e erişimde HTTP 401; USER rolüyle ADMIN endpoint'e erişimde HTTP 403 dönmeli | Auth (RBAC) | ✅ Tamamlandı | Integration | `AuthControllerIntegrationTest` → `protectedEndpoint_withoutToken_returns401`<br>`adminEndpoint_withUserToken_returns403` | ✅ PASS |

---

## FR-007 — FR-009 | KULLANICI YÖNETİMİ

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-007 | Kullanıcı profil bilgilerini görüntüleyebilmeli; profili olmayan kullanıcıda null alanlar dönmeli | UserProfile | ✅ Tamamlandı | Unit | `UserProfileServiceTest` → `getProfile_withValidUser_returnsProfileResponse`<br>`getProfile_withNoProfile_returnsNullFields`<br>`getProfile_withNonExistentUser_throwsNotFound` | ✅ PASS |
| FR-008 | Kullanıcı profil ve tercihlerini güncelleyebilmeli; null alanlar mevcut değerlerin üzerine yazmamalı | UserProfile | ✅ Tamamlandı | Unit | `UserProfileServiceTest` → `updateProfile_withValidRequest_updatesFields`<br>`updateProfile_withNullFields_doesNotOverwrite`<br>`updatePreferences_withValidRequest_savesJsonPreferences` | ✅ PASS |
| FR-009 | Admin, kullanıcı rolü ve aktiflik durumunu güncelleyebilmeli; geçersiz rol HTTP 400 dönmeli | UserManagement | ✅ Tamamlandı | Unit | `UserManagementServiceTest` → `updateUser_withValidRole_updatesRoleAndAudits`<br>`updateUser_withIsActive_updatesActiveStatus`<br>`updateUser_withInvalidRole_throwsBadRequest` | ✅ PASS |
| FR-010 | Admin, kullanıcının şifresini değiştirebilmeli; yanlış mevcut şifrede HTTP 400 dönmeli | UserManagement | ✅ Tamamlandı | Unit | `UserManagementServiceTest` → `changePassword_withCorrectCurrentPassword_encodesAndSaves`<br>`changePassword_withWrongCurrentPassword_throwsBadRequest` | ✅ PASS |
| FR-011 | Kullanıcı FCM push bildirim token'ını güncelleyebilmeli | UserService | ✅ Tamamlandı | Unit | `UserServiceTest` → `updateFcmToken_withValidUser_updatesSavedToken`<br>`updateFcmToken_callsSaveExactlyOnce` | ✅ PASS |

---

## FR-012 — FR-016 | YOĞUNLUK İZLEME

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-012 | Anlık doluluk oranına göre LOW/MEDIUM/HIGH/CRITICAL seviyesi hesaplanmalı | Occupancy | ✅ Tamamlandı | Unit | `OccupancyServiceTest` → `getDensityLevel_withLowValue_returnsLow`<br>`getDensityLevel_withHighValue_returnsHigh`<br>`getDensityLevel_withCriticalValue_returnsCritical`<br>`getDensityLevel_atExactThreshold_returnsHigh` | ✅ PASS |
| FR-013 | Tüm bölgelerin doluluk verisi renk kodu ile birlikte döndürülmeli | Occupancy | ✅ Tamamlandı | Unit + Integration | `OccupancyServiceTest` → `getAllZonesWithOccupancy_returnsCorrectColorCodes`<br>`OccupancyControllerIntegrationTest` → `getHeatmap_withValidToken_returns200AndZones` | ✅ PASS |
| FR-014 | Isı haritası verisi: demo modda DemoProvider, canlı modda veritabanı sorgulanmalı | CrowdMonitor | ✅ Tamamlandı | Unit | `CrowdMonitorServiceTest` → `getHeatmapData_inDemoMode_callsDemoProvider`<br>`getHeatmapData_inLiveMode_queriesRepositories` | ✅ PASS |
| FR-015 | Yoğunluk trendi: son okumalar arasındaki fark %5'i aşınca INCREASING/DECREASING, altında STABLE | CrowdMonitor | ✅ Tamamlandı | Unit | `CrowdMonitorServiceTest` → `trend_INCREASING_whenDensityRisesMoreThan5Pct`<br>`trend_DECREASING_whenDensityDropsMoreThan5Pct`<br>`trend_STABLE_whenDensityChangeLessThan5Pct`<br>`trend_STABLE_whenFewerThan2Readings` | ✅ PASS |
| FR-016 | Saatlik ortalama yoğunluk geçmişi zaman serisi olarak döndürülmeli | CrowdMonitor | ✅ Tamamlandı | Unit | `CrowdMonitorServiceTest` → `getHistory_returnsTimeSeriesPoints`<br>`getHistory_withEmptyReadings_returnsEmptyList` | ✅ PASS |

---

## FR-017 — FR-019 | ENERJİ YÖNETİMİ

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-017 | Düşük yoğunluk + yüksek enerji tüketimi = WASTEFUL; yüksek yoğunluk + yüksek enerji = EFFICIENT | Energy | ✅ Tamamlandı | Unit | `EnergyServiceTest` → `computeStatus_withLowDensityHighEnergy_returnsWasteful`<br>`computeStatus_withHighDensityHighEnergy_returnsEfficient`<br>`computeStatus_withLowDensityLowEnergy_returnsNormal` | ✅ PASS |
| FR-018 | WASTEFUL bölgeler için enerji tasarruf önerileri üretilmeli; yoğun bölgeler için öneri üretilmemeli | Energy | ✅ Tamamlandı | Unit | `EnergyServiceTest` → `getSavingSuggestions_withWastefulZone_returnsSuggestion`<br>`getSavingSuggestions_withHighDensityZone_returnsEmpty` | ✅ PASS |
| FR-019 | Admin dashboard'u: toplam bölge, kritik bölge sayısı, ortalama yoğunluk, uçuş sayısı ve tasarruf fırsatları | AdminDashboard | ✅ Tamamlandı | Unit | `AdminDashboardServiceTest` → `getSummary_aggregatesZonePassengerCounts`<br>`getSummary_countsCriticalZones`<br>`getSummary_calculatesAverageDensityPct`<br>`getSummary_countsActiveFlights`<br>`getSummary_countsSavingSuggestions` | ✅ PASS |

---

## FR-020 — FR-022 | UÇUŞ YÖNETİMİ

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-020 | Tüm uçuşlar listelenebilmeli; uçuş yoksa boş liste dönmeli | Flight | ✅ Tamamlandı | Unit | `FlightServiceTest` → `getAllFlights_returnsAllFlightsAsDtos`<br>`getAllFlights_withNoFlights_returnsEmptyList` | ✅ PASS |
| FR-021 | Uçuş CRUD işlemleri: oluştur/güncelle/sil; var olmayan uçuşta HTTP 404 dönmeli | Flight | ✅ Tamamlandı | Unit | `FlightServiceTest` → `createFlight_withValidRequest_savesFlight`<br>`updateFlight_withValidRequest_updatesAndSaves`<br>`deleteFlight_withValidId_deletesFromRepository`<br>`deleteFlight_withNonExistentId_throwsNotFound` | ✅ PASS |
| FR-022 | Uçuşa kalanı dakikası hesaplanmalı: gelecek uçuş pozitif, geçmiş uçuş negatif değer vermeli | Flight | ✅ Tamamlandı | Unit | `FlightServiceTest` → `getMinutesToDeparture_withFutureDeparture_returnsPositive`<br>`getMinutesToDeparture_withPastDeparture_returnsNegative` | ✅ PASS |

---

## FR-023 — FR-025 | BİLET YÖNETİMİ

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-023 | PNR kodu ile bilet sorgulanabilmeli; PNR büyük harfe çevrilmeli | Ticket | ✅ Tamamlandı | Unit | `TicketServiceTest` → `lookupByPnr_withValidPnr_returnsTicketDetail`<br>`lookupByPnr_withInvalidPnr_throwsNotFound`<br>`lookupByPnr_convertsToUppercase` | ✅ PASS |
| FR-024 | Yolcu bileti talep edebilmeli; başkasına ait bilet talep edilemez (HTTP 409) | Ticket | ✅ Tamamlandı | Unit | `TicketServiceTest` → `claimTicket_withUnclaimedTicket_assignsUserAndReturns`<br>`claimTicket_withAlreadyOwnedByCurrentUser_throwsConflict`<br>`claimTicket_withTicketOwnedByOtherUser_throwsConflict` | ✅ PASS |
| FR-025 | Yolcu biletini bırakabilmeli; sahibi olmayan yolcu biletini bırakamaz (HTTP 403) | Ticket | ✅ Tamamlandı | Unit | `TicketServiceTest` → `unclaimTicket_byOwner_removesUserFromTicket`<br>`unclaimTicket_byNonOwner_throwsForbidden` | ✅ PASS |

---

## FR-026 — FR-028 | ROTA & YÖNLENDİRME

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-026 | Dijkstra algoritması ile en kısa/dengeli rota bulunmalı; ulaşılamaz hedef için boş sonuç dönmeli | Pathfinding | ✅ Tamamlandı | Unit | `DijkstraServiceTest` → `findPath_withDirectEdge_returnsOneHopPath`<br>`findPath_withTwoHops_prefersShortestPath`<br>`findPath_unreachableGoal_returnsUnreachableResult`<br>`findPath_sameStartAndEnd_returnsTrivialSingleSegmentPath` | ✅ PASS |
| FR-027 | Aktif bileti olmayan veya geçmiş uçuşu olan yolcuya rota önerilmemeli | Route | ✅ Tamamlandı | Unit | `RouteServiceTest` → `getSuggestedRoute_withNoActiveTickets_throwsNotFound`<br>`getSuggestedRoute_withPastFlightOnly_throwsNotFound` | ✅ PASS |
| FR-028 | Sessiz alternatif bölgeler: yüksek yoğunluklu bölgeler ve hedef bölge filtrelenmeli; yoğunluğa göre sıralanmalı | Route | ✅ Tamamlandı | Unit | `RouteServiceTest` → `getQuietAlternatives_filtersHighDensityZones`<br>`getQuietAlternatives_excludesTargetZone`<br>`getQuietAlternatives_sortedByDensityAscending`<br>`getQuietAlternatives_withAllHighDensity_returnsEmpty` | ✅ PASS |

---

## FR-029 — FR-031 | BİLDİRİM SİSTEMİ

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-029 | Kritik yoğunluk eşiği aşıldığında ilgili bölgedeki kullanıcılara otomatik uyarı gönderilmeli | Notification | ✅ Tamamlandı | Unit | `CrowdAlertSchedulerTest` → `checkAndAlertCriticalZones_withCriticalZone_sendsAlert`<br>`checkAndAlertCriticalZones_withNoCriticalZones_sendsNoAlerts`<br>`checkAndAlertCriticalZones_withNullDensity_skipsZone` | ✅ PASS |
| FR-030 | Rate limiting: kısa sürede aynı kullanıcıya birden fazla bildirim gönderilmemeli | Notification | ✅ Tamamlandı | Unit | `NotificationServiceTest` → `triggerCrowdAlert_withRateLimitedUser_skipsNotification`<br>`triggerCrowdAlert_withEligibleUser_sendsNotification` | ✅ PASS |
| FR-031 | Admin manuel bildirim gönderebilmeli; kullanıcı bildirimlerini okuyabilmeli/silebilmeli | Notification | ✅ Tamamlandı | Unit | `NotificationServiceTest` → `sendManual_withValidRequest_savesAndReturnsResponse`<br>`getMyNotifications_returnsListForUser`<br>`markAsRead_withOwner_setsReadTrue`<br>`markAsRead_withDifferentUser_throwsForbidden`<br>`deleteNotification_withOwner_deletesNotification` | ✅ PASS |

---

## FR-032 — FR-033 | YAPAY ZEKA TAHMİN

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-032 | Yüksek riskli bölgeler filtrelenebilmeli; risk olmayan bölgeler döndürülmemeli | AI Prediction | ✅ Tamamlandı | Unit | `AIPredictionServiceTest` → `getHighRiskZones_returnsOnlyHighRiskPredictions`<br>`getHighRiskZones_withNoHighRisk_returnsEmpty` | ✅ PASS |
| FR-033 | AI servisi erişilemez olduğunda exception yayılmamalı, sistem çalışmaya devam etmeli | AI Prediction | ✅ Tamamlandı | Unit | `AIPredictionServiceTest` → `fetchAndStorePredictions_whenAiServiceFails_doesNotPropagateException`<br>`getPredictionForZone_withNoCache_callsAiClient` | ✅ PASS |

---

## FR-034 — FR-035 | CHATBOT / AI ASİSTAN

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-034 | Chatbot boş/null mesajda varsayılan yanıt dönmeli; geçerli mesajda AI provider kullanılmalı | Chatbot | ✅ Tamamlandı | Unit | `ChatbotServiceTest` → `ask_withNullMessage_returnsDefaultPromptResponse`<br>`ask_withBlankMessage_returnsDefaultPromptResponse`<br>`ask_withValidMessage_usesDefaultProvider` | ✅ PASS |
| FR-035 | Kullanıcı chatbot provider seçebilmeli; erişilemez provider seçilince uyarı mesajı dönmeli | Chatbot | ✅ Tamamlandı | Unit | `ChatbotServiceTest` → `ask_withExplicitProvider_usesRequestedProvider`<br>`ask_withUnavailableProvider_returnsUnavailableMessage`<br>`ask_withUnknownProvider_fallsBackToDefault` | ✅ PASS |

---

## FR-036 — FR-037 | SADAKAT & LOUNGE

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-036 | Sadakat puanı eklenince bakiye güncellenmeli; GOLD/PLATINUM eşiği geçilince tier yükselmeli | Loyalty | ✅ Tamamlandı | Unit | `LoyaltyServiceTest` → `addPoints_updatesBalanceAndSavesTx`<br>`addPoints_crossingGoldThreshold_updatesTierToGold`<br>`spendPoints_withInsufficientBalance_throwsBadRequest`<br>`addPoints_withPlatinumPoints_setsPlatinum` | ✅ PASS |
| FR-037 | Sessiz lounge bölgeleri %50 doluluk altında filtrelenmeli; yoğunluğa göre sıralanmalı | Lounge | ✅ Tamamlandı | Unit | `LoungeServiceTest` → `getQuietLounges_returnsOnlyLoungesWithDensityBelow50Pct`<br>`getQuietLounges_sortedByDensityAscending`<br>`getBestLounge_returnsLowestDensityLounge` | ✅ PASS |

---

## FR-038 — FR-040 | RAPORLAMA & SİSTEM

| Req ID | Açıklama | Modül | Durum | Test Tipi | Test Kanıtı | Test Sonucu |
|--------|----------|-------|-------|-----------|-------------|-------------|
| FR-038 | Yoğunluk, enerji tasarrufu ve kullanıcı kayıt raporları üretilebilmeli; bilinmeyen rapor türünde hata raporu dönmeli | Report | ✅ Tamamlandı | Unit | `ReportNarrativeServiceTest` → `generateReport_withUnknownType_returnsErrorReport`<br>`generateReport_withUserRegistrationType_doesNotThrow`<br>`generateReport_whenJdbcFails_returnsErrorReport`<br>`generateReport_withEnergySavingsType_doesNotThrow`<br>`generateReport_withOccupancyGeneralType_doesNotThrow` | ✅ PASS |
| FR-039 | Sistem bölge ayarları güncellenebilmeli; admin işlemleri audit log'a kaydedilmeli | SystemSettings | ✅ Tamamlandı | Unit | `SystemSettingsServiceTest` → `updateZoneThreshold_withValidZone_updatesThresholdAndAudits`<br>`updateZoneThreshold_withNonExistentZone_throwsNotFound`<br>`checkServicesHealth_returnsThreeServiceResults`<br>`checkServicesHealth_whenDbFails_returnsDownForPostgres` | ✅ PASS |
| FR-040 | E-posta doğrulama kodu üretilmeli; cooldown süresi dolmadan yeni kod üretilmemeli; süresi dolmuş kod reddedilmeli | Verification | ✅ Tamamlandı | Unit | `VerificationServiceTest` → `generateAndSend_withNoCooldown_savesCodeAndSendsEmail`<br>`generateAndSend_withinCooldown_returnsFalse`<br>`verify_withValidCode_returnsTrue_andMarksConsumed`<br>`verify_withExpiredCode_returnsFalse`<br>`generateAndSend_generatesExactly6DigitCode` | ✅ PASS |
| FR-041 | Veri saklama politikası: eski occupancy, enerji ve tahmin kayıtları otomatik silinmeli | DataRetention | ✅ Tamamlandı | Unit | `DataRetentionSchedulerTest` → `runRetention_deletesOccupancyEnergryAndPredictionRecords`<br>`runRetention_logsSystemAuditWithSummary`<br>`runRetention_withNoOldData_deletesZeroRows` | ✅ PASS |
| FR-042 | Kritik admin işlemleri audit log'a kaydedilmeli; DB hatası olsa bile exception yayılmamalı | AuditLog | ✅ Tamamlandı | Unit | `AuditLogServiceTest` → `log_withValidParams_executesJdbcUpdate`<br>`log_whenJdbcThrowsException_doesNotPropagateError`<br>`logSystem_callsJdbcWithNullActorId` | ✅ PASS |

---

## Özet

| | Adet |
|--|--|
| **Toplam Fonksiyonel Gereksinim** | 42 |
| **Unit Test ile Doğrulanan** | 40 |
| **Integration Test ile Doğrulanan** | 8 |
| **Başarılı (PASS)** | **42** |
| **Başarısız (FAIL)** | **0** |

### CI/CD Kanıtı
- **GitHub Actions Workflow:** `Eco-Terminal CI` — Run #61
- **Status:** ✅ Success (6m 37s)
- **Jobs:** Backend Unit Tests ✅ | Backend Integration Tests ✅ | Frontend Tests ✅ | OWASP Dependency Check ✅ | Docker Build Check ✅
- **Artifact:** `backend-unit-test-reports` (Surefire XML raporları)

### Yerel Test Komutu
```bash
cd backend
./mvnw test -Dspring.profiles.active=test -Dsurefire.excludes="**/*IntegrationTest*"
# Çıktı: Tests run: 222, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS
```
