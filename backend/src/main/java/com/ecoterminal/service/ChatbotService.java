package com.ecoterminal.service;

import com.ecoterminal.model.dto.ChatbotResponse;
import com.ecoterminal.model.dto.ProviderInfoResponse;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.model.entity.ZoneType;
import com.ecoterminal.repository.EcoWalletRepository;
import com.ecoterminal.repository.NotificationRepository;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneRepository;
import com.ecoterminal.service.chatbot.ChatContext;
import com.ecoterminal.service.chatbot.ChatbotProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LLM tabanlı chatbot servisi.
 *
 * Mimari:
 *   1. İstekten sağlayıcı adı okunur ("cloud" / "local"). Yoksa default kullanılır.
 *   2. RAG: DB'den gerçek zamanlı veriler çekilir (yoğunluk, uçuş, eco puan).
 *   3. ChatContext oluşturulur ve seçili provider'a iletilir.
 *   4. Provider LLM cevabını döner; ChatbotResponse içinde sarılır.
 */
@Slf4j
@Service
public class ChatbotService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Istanbul"));

    private static final Map<ZoneType, String> TYPE_TR = Map.of(
            ZoneType.GATE,     "Kapı",
            ZoneType.LOUNGE,   "Bekleme Salonu",
            ZoneType.SECURITY, "Güvenlik",
            ZoneType.CHECKIN,  "Check-in",
            ZoneType.RETAIL,   "Mağaza",
            ZoneType.OTHER,    "Diğer"
    );

    private final Map<String, ChatbotProvider> providers;
    private final ZoneRepository              zoneRepository;
    private final OccupancyReadingRepository  occupancyRepo;
    private final TicketRepository            ticketRepository;
    private final EcoWalletRepository         walletRepository;
    private final NotificationRepository      notificationRepository;
    private final String                      defaultProvider;

    public ChatbotService(
            List<ChatbotProvider> providerList,
            ZoneRepository zoneRepository,
            OccupancyReadingRepository occupancyRepo,
            TicketRepository ticketRepository,
            EcoWalletRepository walletRepository,
            NotificationRepository notificationRepository,
            @Value("${app.chatbot.default-provider:cloud}") String defaultProvider) {

        this.providers       = providerList.stream()
                .collect(Collectors.toMap(ChatbotProvider::getProviderName, p -> p));
        this.zoneRepository  = zoneRepository;
        this.occupancyRepo   = occupancyRepo;
        this.ticketRepository = ticketRepository;
        this.walletRepository = walletRepository;
        this.notificationRepository = notificationRepository;
        this.defaultProvider  = defaultProvider;
    }

    // ── Ana entry point ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChatbotResponse ask(String message, String requestedProvider, Long userId) {
        if (message == null || message.isBlank()) {
            String pName = resolveProviderName(requestedProvider);
            return ChatbotResponse.of("Lütfen bir soru yazın.", pName);
        }

        // Sağlayıcı seçimi
        String providerKey = resolveProviderName(requestedProvider);
        ChatbotProvider provider = providers.get(providerKey);

        if (provider == null) {
            log.warn("Bilinmeyen sağlayıcı '{}', default'a düşülüyor: {}", requestedProvider, defaultProvider);
            provider = providers.get(defaultProvider);
        }

        if (provider == null || !provider.isAvailable()) {
            String displayName = provider != null ? provider.getDisplayName() : providerKey;
            return ChatbotResponse.of(
                    displayName + " şu an kullanılamıyor. Lütfen ayarlardan farklı bir mod seçin.",
                    providerKey
            );
        }

        // RAG: gerçek zamanlı bağlam oluştur
        ChatContext context = buildContext(userId);

        log.debug("Chatbot isteği: userId={}, provider={}, mesaj='{}'", userId, providerKey, message);

        // LLM'e ilet (zengin metadata destekli)
        return provider.generateRichResponse(message, context);
    }

    /** Mevcut provider'ların listesini ve durumlarını döner (ayar ekranı için). */
    public List<ProviderInfoResponse> getProviders() {
        return providers.values().stream()
                .sorted(Comparator.comparing(ChatbotProvider::getProviderName))
                .map(p -> new ProviderInfoResponse(p.getProviderName(), p.getDisplayName(), p.isAvailable()))
                .toList();
    }

    // ── RAG: Bağlam Oluşturma ────────────────────────────────────────────────

    private ChatContext buildContext(Long userId) {
        // Zaman
        String now = TIME_FMT.format(Instant.now());

        // Zone yoğunlukları
        List<Zone> zones = Collections.emptyList();
        try {
            zones = zoneRepository.findByStatus(ZoneStatus.ACTIVE);
        } catch (Exception e) {
            log.warn("Zone listesi çekilemedi: {}", e.getMessage());
        }

        Map<Long, Float> densityMap = Collections.emptyMap();
        try {
            densityMap = buildDensityMap();
        } catch (Exception e) {
            log.warn("Yoğunluk verisi çekilemedi: {}", e.getMessage());
        }

        final Map<Long, Float> dm = densityMap;

        List<ChatContext.ZoneInfo> hotZones = zones.stream()
                .filter(z -> {
                    Float d = dm.get(z.getZoneId());
                    return d != null && d >= 0.60f;
                })
                .sorted(Comparator.comparing((Zone z) -> dm.getOrDefault(z.getZoneId(), 0f)).reversed())
                .limit(5)
                .map(z -> new ChatContext.ZoneInfo(
                        z.getZoneName(),
                        TYPE_TR.getOrDefault(z.getType(), z.getType().name()),
                        Math.round(dm.get(z.getZoneId()) * 100)))
                .toList();

        List<ChatContext.ZoneInfo> quietZones = zones.stream()
                .filter(z -> {
                    Float d = dm.get(z.getZoneId());
                    return d != null && d < 0.40f;
                })
                .sorted(Comparator.comparing(z -> dm.getOrDefault(z.getZoneId(), 1f)))
                .limit(5)
                .map(z -> new ChatContext.ZoneInfo(
                        z.getZoneName(),
                        TYPE_TR.getOrDefault(z.getType(), z.getType().name()),
                        Math.round(dm.get(z.getZoneId()) * 100)))
                .toList();

        int avgPct = (int) zones.stream()
                .filter(z -> dm.containsKey(z.getZoneId()))
                .mapToDouble(z -> dm.get(z.getZoneId()))
                .average()
                .orElse(0.0) * 100;

        // Uçuş bilgileri
        List<ChatContext.FlightInfo> flights = Collections.emptyList();
        try {
            flights = ticketRepository.findActiveTicketsWithFlight(userId).stream()
                    .map(t -> {
                        var f = t.getFlight();
                        return new ChatContext.FlightInfo(
                                f.getFlightCode(),
                                f.getDestination(),
                                f.getGate() != null ? f.getGate().getZoneName() : null,
                                TIME_FMT.format(f.getDepartureTime()),
                                statusTr(f.getStatus().name())
                        );
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Uçuş bilgisi çekilemedi: {}", e.getMessage());
        }

        // Eco puan
        Integer ecoPoints = null;
        String tierLevel = "Üye";
        try {
            var wallet = walletRepository.findByUser_UserId(userId).orElse(null);
            if (wallet != null) {
                ecoPoints = wallet.getCurrentBalance();
                tierLevel = wallet.getTierLevel() != null ? wallet.getTierLevel().name() : "GREEN";
            }
        } catch (Exception e) {
            log.warn("Eco cüzdan bilgisi çekilemedi: {}", e.getMessage());
        }

        // Okunmamış bildirim sayısı
        long unreadCount = 0L;
        try {
            unreadCount = notificationRepository.countByUser_UserIdAndIsReadFalse(userId);
        } catch (Exception e) {
            log.warn("Bildirim sayısı çekilemedi: {}", e.getMessage());
        }

        return new ChatContext(flights, ecoPoints, tierLevel, hotZones, quietZones, avgPct, now, unreadCount);
    }

    private Map<Long, Float> buildDensityMap() {
        return occupancyRepo.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getZone().getZoneId(),
                        OccupancyReading::getDensityPct,
                        (a, b) -> a
                ));
    }

    private String resolveProviderName(String requested) {
        if (requested == null || requested.isBlank()) return defaultProvider;
        return requested.toLowerCase(Locale.ROOT).trim();
    }

    private String statusTr(String status) {
        return switch (status) {
            case "SCHEDULED" -> "Planlandı";
            case "BOARDING"  -> "Biniş Başladı";
            case "DEPARTED"  -> "Kalktı";
            case "DELAYED"   -> "Gecikti";
            case "CANCELLED" -> "İptal";
            default          -> status;
        };
    }
}
