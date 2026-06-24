package com.ecoterminal.service;

import com.ecoterminal.model.dto.ChatbotResponse;
import com.ecoterminal.model.dto.ProviderInfoResponse;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.repository.EcoWalletRepository;
import com.ecoterminal.repository.NotificationRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneRepository;
import com.ecoterminal.service.chatbot.ChatbotProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatbotService Unit Tests")
class ChatbotServiceTest {

    @Mock private ZoneRepository             zoneRepository;
    @Mock private OccupancyReadingRepository occupancyRepo;
    @Mock private TicketRepository           ticketRepository;
    @Mock private EcoWalletRepository        walletRepository;
    @Mock private NotificationRepository     notificationRepository;

    // Provider mock'ları
    @Mock private ChatbotProvider cloudProvider;
    @Mock private ChatbotProvider localProvider;

    private ChatbotService chatbotService;

    @BeforeEach
    void setUp() {
        // cloud provider kurulumu
        when(cloudProvider.getProviderName()).thenReturn("cloud");
        when(cloudProvider.getDisplayName()).thenReturn("Gemini Pro");

        // local provider kurulumu
        when(localProvider.getProviderName()).thenReturn("local");
        when(localProvider.getDisplayName()).thenReturn("Yerel Bot");

        chatbotService = new ChatbotService(
                List.of(cloudProvider, localProvider),
                zoneRepository,
                occupancyRepo,
                ticketRepository,
                walletRepository,
                notificationRepository,
                "cloud"  // default provider
        );
    }

    // ── ask Tests ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("ask_withNullMessage_returnsDefaultPromptResponse")
    void ask_withNullMessage_returnsDefaultPromptResponse() {
        // when
        ChatbotResponse result = chatbotService.ask(null, null, 1L);

        // then
        assertThat(result.reply()).isEqualTo("Lütfen bir soru yazın.");
        verify(cloudProvider, never()).generateRichResponse(any(), any());
    }

    @Test
    @DisplayName("ask_withBlankMessage_returnsDefaultPromptResponse")
    void ask_withBlankMessage_returnsDefaultPromptResponse() {
        // when
        ChatbotResponse result = chatbotService.ask("   ", null, 1L);

        // then
        assertThat(result.reply()).isEqualTo("Lütfen bir soru yazın.");
    }

    @Test
    @DisplayName("ask_withValidMessage_usesDefaultProvider")
    void ask_withValidMessage_usesDefaultProvider() {
        // given
        when(cloudProvider.isAvailable()).thenReturn(true);
        when(zoneRepository.findByStatus(ZoneStatus.ACTIVE)).thenReturn(List.of());
        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of());
        when(ticketRepository.findActiveTicketsWithFlight(1L)).thenReturn(List.of());
        when(walletRepository.findByUser_UserId(1L)).thenReturn(java.util.Optional.empty());
        when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(0L);
        when(cloudProvider.generateRichResponse(eq("Terminal doluluk durumu nedir?"), any()))
                .thenReturn(ChatbotResponse.of("Gate A1 şu an yoğun.", "cloud"));

        // when
        ChatbotResponse result = chatbotService.ask("Terminal doluluk durumu nedir?", null, 1L);

        // then
        assertThat(result.reply()).isEqualTo("Gate A1 şu an yoğun.");
        verify(cloudProvider).generateRichResponse(any(), any());
        verify(localProvider, never()).generateRichResponse(any(), any());
    }

    @Test
    @DisplayName("ask_withExplicitProvider_usesRequestedProvider")
    void ask_withExplicitProvider_usesRequestedProvider() {
        // given
        when(localProvider.isAvailable()).thenReturn(true);
        when(zoneRepository.findByStatus(any())).thenReturn(List.of());
        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of());
        when(ticketRepository.findActiveTicketsWithFlight(1L)).thenReturn(List.of());
        when(walletRepository.findByUser_UserId(1L)).thenReturn(java.util.Optional.empty());
        when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(0L);
        when(localProvider.generateRichResponse(any(), any()))
                .thenReturn(ChatbotResponse.of("Yerel cevap.", "local"));

        // when
        ChatbotResponse result = chatbotService.ask("Soru", "local", 1L);

        // then
        assertThat(result.reply()).isEqualTo("Yerel cevap.");
        verify(localProvider).generateRichResponse(any(), any());
        verify(cloudProvider, never()).generateRichResponse(any(), any());
    }

    @Test
    @DisplayName("ask_withUnavailableProvider_returnsUnavailableMessage")
    void ask_withUnavailableProvider_returnsUnavailableMessage() {
        // given
        when(cloudProvider.isAvailable()).thenReturn(false);
        when(zoneRepository.findByStatus(any())).thenReturn(List.of());
        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of());
        when(ticketRepository.findActiveTicketsWithFlight(1L)).thenReturn(List.of());
        when(walletRepository.findByUser_UserId(1L)).thenReturn(java.util.Optional.empty());
        when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(0L);

        // when
        ChatbotResponse result = chatbotService.ask("Soru", "cloud", 1L);

        // then
        assertThat(result.reply()).contains("kullanılamıyor");
        verify(cloudProvider, never()).generateRichResponse(any(), any());
    }

    @Test
    @DisplayName("ask_withUnknownProvider_fallsBackToDefault")
    void ask_withUnknownProvider_fallsBackToDefault() {
        // given
        when(cloudProvider.isAvailable()).thenReturn(true);
        when(zoneRepository.findByStatus(any())).thenReturn(List.of());
        when(occupancyRepo.findLatestPerZone()).thenReturn(List.of());
        when(ticketRepository.findActiveTicketsWithFlight(1L)).thenReturn(List.of());
        when(walletRepository.findByUser_UserId(1L)).thenReturn(java.util.Optional.empty());
        when(notificationRepository.countByUser_UserIdAndIsReadFalse(1L)).thenReturn(0L);
        when(cloudProvider.generateRichResponse(any(), any()))
                .thenReturn(ChatbotResponse.of("Varsayılan cevap.", "cloud"));

        // when — "bilinmeyen-provider" istenirse cloud'a düşmeli
        ChatbotResponse result = chatbotService.ask("Soru", "bilinmeyen-provider", 1L);

        // then
        verify(cloudProvider).generateRichResponse(any(), any());
        assertThat(result.reply()).isEqualTo("Varsayılan cevap.");
    }

    // ── getProviders Tests ────────────────────────────────────────────────

    @Test
    @DisplayName("getProviders_returnsAllRegisteredProviders")
    void getProviders_returnsAllRegisteredProviders() {
        // given
        when(cloudProvider.isAvailable()).thenReturn(true);
        when(localProvider.isAvailable()).thenReturn(false);

        // when
        List<ProviderInfoResponse> providers = chatbotService.getProviders();

        // then
        assertThat(providers).hasSize(2);
        assertThat(providers.stream().map(ProviderInfoResponse::name))
                .containsExactlyInAnyOrder("cloud", "local");
    }

    @Test
    @DisplayName("getProviders_reflectsProviderAvailability")
    void getProviders_reflectsProviderAvailability() {
        // given
        when(cloudProvider.isAvailable()).thenReturn(true);
        when(localProvider.isAvailable()).thenReturn(false);

        // when
        List<ProviderInfoResponse> providers = chatbotService.getProviders();

        // then
        ProviderInfoResponse cloud = providers.stream()
                .filter(p -> "cloud".equals(p.name())).findFirst().orElseThrow();
        ProviderInfoResponse local = providers.stream()
                .filter(p -> "local".equals(p.name())).findFirst().orElseThrow();

        assertThat(cloud.available()).isTrue();
        assertThat(local.available()).isFalse();
    }
}
