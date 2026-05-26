package com.ecoterminal.service;

import com.ecoterminal.model.dto.ChatbotResponse;
import com.ecoterminal.model.entity.DensityLevel;
import com.ecoterminal.model.entity.OccupancyReading;
import com.ecoterminal.model.entity.Ticket;
import com.ecoterminal.model.entity.Zone;
import com.ecoterminal.model.entity.ZoneStatus;
import com.ecoterminal.model.entity.ZoneType;
import com.ecoterminal.repository.OccupancyReadingRepository;
import com.ecoterminal.repository.TicketRepository;
import com.ecoterminal.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Kural tabanlı chatbot servisi.
 * Harici LLM kullanmaz — keyword eşleştirme + DB sorguları ile çalışır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatbotService {

    private final ZoneRepository zoneRepository;
    private final OccupancyReadingRepository occupancyReadingRepository;
    private final TicketRepository ticketRepository;

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.of("Europe/Istanbul"));

    private static final String HELP_MESSAGE =
            "Şu konularda yardımcı olabilirim:\n" +
            "• \"En sakin lounge nerede?\" — boş bölge önerisi\n" +
            "• \"Gate A1 dolu mu?\" — belirli bölge doluluğu\n" +
            "• \"Nereye gideyim?\" — en sakin 3 bölge önerisi\n" +
            "• \"Uçuşum nerede?\" — bilet ve kapı bilgisi\n" +
            "• \"Genel durum nasıl?\" — terminal yoğunluk özeti";

    // ── Tip isimlerinin Türkçe karşılıkları ─────────────────────────────────
    private static final Map<ZoneType, String> TYPE_TR = Map.of(
            ZoneType.GATE,     "Kapı",
            ZoneType.LOUNGE,   "Bekleme Salonu",
            ZoneType.SECURITY, "Güvenlik",
            ZoneType.CHECKIN,  "Check-in",
            ZoneType.RETAIL,   "Mağaza",
            ZoneType.OTHER,    "Diğer"
    );

    // ── Yoğunluk seviyesinin Türkçe karşılıkları ────────────────────────────
    private static final Map<DensityLevel, String> DENSITY_TR = Map.of(
            DensityLevel.LOW,      "Sakin",
            DensityLevel.MEDIUM,   "Orta yoğun",
            DensityLevel.HIGH,     "Yoğun",
            DensityLevel.CRITICAL, "Kritik seviyede yoğun"
    );

    // ── Ana entry point ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ChatbotResponse ask(String message, Long userId) {
        if (message == null || message.isBlank()) {
            return ChatbotResponse.of(HELP_MESSAGE);
        }

        // Türkçe büyük/küçük harf dönüşümü için Locale.ROOT yeterli (ASCII benzer)
        String lower = message.toLowerCase(Locale.ROOT)
                               .replace("ı", "i")
                               .replace("ö", "o")
                               .replace("ü", "u")
                               .replace("ş", "s")
                               .replace("ç", "c")
                               .replace("ğ", "g");

        // Verileri bir kez çek — tüm intent handler'lar paylaşır
        List<Zone> zones = zoneRepository.findByStatus(ZoneStatus.ACTIVE);
        Map<Long, Float> densityMap = buildDensityMap();

        // ── Intent 1: Uçuş / kapı bilgisi ───────────────────────────────────
        if (containsAny(lower, "ucus", "ucak", "ucagim", "kapim", "bilet", "gate",
                        "kalkis", "nerede kalkiyor", "ne zaman")) {
            return handleFlightInfo(userId);
        }

        // ── Intent 2: En boş/sakin bölge ────────────────────────────────────
        if (containsAny(lower, "en bos", "en sakin", "en az", "sessiz", "kalabalik degil",
                        "bos lounge", "bos kapi", "bos alan")) {
            ZoneType filter = detectZoneTypeFilter(lower);
            return handleLeastDense(zones, densityMap, filter);
        }

        // ── Intent 3: Öneri / yönlendirme ───────────────────────────────────
        if (containsAny(lower, "nereye", "oneri", "tavsiye", "yonlendir",
                        "gideyim", "gitsem", "nereye gitsem", "sakin yer")) {
            return handleRecommendation(zones, densityMap);
        }

        // ── Intent 4: Belirli zone doluluğu ─────────────────────────────────
        Optional<Zone> matched = findZoneInMessage(lower, zones);
        if (matched.isPresent()) {
            return handleZoneOccupancy(matched.get(), densityMap);
        }

        // ── Intent 5: Genel yoğunluk özeti ──────────────────────────────────
        if (containsAny(lower, "durum", "genel", "ozet", "kac zone", "terminal",
                        "yogunluk", "anlık", "hepsi", "tumu", "nasil")) {
            return handleSummary(zones, densityMap);
        }

        // ── Tanınmayan ───────────────────────────────────────────────────────
        log.debug("Chatbot: tanınmayan niyet — mesaj='{}'", message);
        return ChatbotResponse.of(HELP_MESSAGE);
    }

    // ── Intent Handler'lar ───────────────────────────────────────────────────

    private ChatbotResponse handleFlightInfo(Long userId) {
        List<Ticket> tickets = ticketRepository.findActiveTicketsWithFlight(userId);

        if (tickets.isEmpty()) {
            return ChatbotResponse.of(
                    "Aktif biletiniz bulunamadı. Uçuş bilgilerinizi \"Uçuşlarım\" sayfasından kontrol edebilirsiniz."
            );
        }

        StringBuilder sb = new StringBuilder();
        List<String> gates = new ArrayList<>();

        for (Ticket ticket : tickets) {
            var flight = ticket.getFlight();
            String flightCode = flight.getFlightCode();
            String destination = flight.getDestination();
            String depTime = TIME_FMT.format(flight.getDepartureTime());
            String status = statusTr(flight.getStatus().name());

            sb.append(String.format("✈ %s → %s%n", flightCode, destination));
            sb.append(String.format("  Kalkış: %s | Durum: %s%n", depTime, status));

            if (flight.getGate() != null) {
                String gateName = flight.getGate().getZoneName();
                sb.append(String.format("  Kapı: %s%n", gateName));
                gates.add(gateName);
            } else {
                sb.append("  Kapı: Henüz atanmadı\n");
            }
            sb.append("\n");
        }

        sb.append("İyi uçuşlar! 🌿");

        return ChatbotResponse.withZones(sb.toString().trim(), gates.isEmpty() ? null : gates);
    }

    private ChatbotResponse handleLeastDense(List<Zone> zones, Map<Long, Float> densityMap,
                                              ZoneType typeFilter) {
        List<Zone> candidates = zones.stream()
                .filter(z -> typeFilter == null || z.getType() == typeFilter)
                .filter(z -> densityMap.containsKey(z.getZoneId()))
                .sorted(Comparator.comparing(z -> densityMap.getOrDefault(z.getZoneId(), 1f)))
                .toList();

        if (candidates.isEmpty()) {
            String typeMsg = typeFilter != null ? " (" + TYPE_TR.get(typeFilter) + ")" : "";
            return ChatbotResponse.of("Şu an için" + typeMsg + " yoğunluk verisi bulunamadı.");
        }

        Zone best = candidates.get(0);
        float density = densityMap.getOrDefault(best.getZoneId(), 0f);
        int pct = Math.round(density * 100);
        DensityLevel level = DensityLevel.of(density);

        String reply = String.format(
                "Şu an en sakin bölge \uD83D\uDCCD %s (%s).\n" +
                "Doluluk: %%%d — %s\n" +
                "Rahatça bekleyebilirsiniz. 🌿",
                best.getZoneName(),
                TYPE_TR.getOrDefault(best.getType(), best.getType().name()),
                pct,
                DENSITY_TR.getOrDefault(level, level.name())
        );

        return ChatbotResponse.withZones(reply, List.of(best.getZoneName()));
    }

    private ChatbotResponse handleRecommendation(List<Zone> zones, Map<Long, Float> densityMap) {
        List<Zone> sorted = zones.stream()
                .filter(z -> densityMap.containsKey(z.getZoneId()))
                .sorted(Comparator.comparing(z -> densityMap.getOrDefault(z.getZoneId(), 1f)))
                .limit(3)
                .toList();

        if (sorted.isEmpty()) {
            return ChatbotResponse.of("Şu an için yoğunluk verisi bulunamadı.");
        }

        StringBuilder sb = new StringBuilder("Size şu bölgeleri öneriyorum:\n\n");
        List<String> names = new ArrayList<>();

        for (int i = 0; i < sorted.size(); i++) {
            Zone z = sorted.get(i);
            float density = densityMap.getOrDefault(z.getZoneId(), 0f);
            int pct = Math.round(density * 100);
            DensityLevel level = DensityLevel.of(density);
            sb.append(String.format("%d. %s — %%%d (%s)\n", i + 1,
                    z.getZoneName(), pct, DENSITY_TR.getOrDefault(level, level.name())));
            names.add(z.getZoneName());
        }

        sb.append("\nBu bölgeler şu an en az kalabalık.");
        return ChatbotResponse.withZones(sb.toString(), names);
    }

    private ChatbotResponse handleZoneOccupancy(Zone zone, Map<Long, Float> densityMap) {
        Float density = densityMap.get(zone.getZoneId());

        if (density == null) {
            return ChatbotResponse.of(
                    zone.getZoneName() + " için anlık veri bulunamadı. " +
                    "Sensörden henüz okuma gelmemiş olabilir."
            );
        }

        int pct = Math.round(density * 100);
        DensityLevel level = DensityLevel.of(density);
        String levelTr = DENSITY_TR.getOrDefault(level, level.name());
        String emoji = switch (level) {
            case LOW      -> "🟢";
            case MEDIUM   -> "🟡";
            case HIGH     -> "🟠";
            case CRITICAL -> "🔴";
        };

        String reply = String.format(
                "%s %s şu an %s durumda.\n" +
                "Doluluk oranı: %%%d\n" +
                "Bölge tipi: %s",
                emoji,
                zone.getZoneName(),
                levelTr.toLowerCase(),
                pct,
                TYPE_TR.getOrDefault(zone.getType(), zone.getType().name())
        );

        return ChatbotResponse.withZones(reply, List.of(zone.getZoneName()));
    }

    private ChatbotResponse handleSummary(List<Zone> zones, Map<Long, Float> densityMap) {
        long total    = zones.size();
        long withData = zones.stream().filter(z -> densityMap.containsKey(z.getZoneId())).count();
        long empty    = zones.stream().filter(z -> {
            Float d = densityMap.get(z.getZoneId());
            return d != null && d < 0.30f;
        }).count();
        long moderate = zones.stream().filter(z -> {
            Float d = densityMap.get(z.getZoneId());
            return d != null && d >= 0.30f && d < 0.60f;
        }).count();
        long busy     = zones.stream().filter(z -> {
            Float d = densityMap.get(z.getZoneId());
            return d != null && d >= 0.60f && d < 0.85f;
        }).count();
        long critical = zones.stream().filter(z -> {
            Float d = densityMap.get(z.getZoneId());
            return d != null && d >= 0.85f;
        }).count();

        OptionalDouble avgOpt = zones.stream()
                .filter(z -> densityMap.containsKey(z.getZoneId()))
                .mapToDouble(z -> densityMap.get(z.getZoneId()))
                .average();
        int avgPct = avgOpt.isPresent() ? Math.round((float) avgOpt.getAsDouble() * 100) : 0;

        String summary = String.format(
                "📊 Terminal Anlık Durum (%d/%d bölgede veri var)\n\n" +
                "🟢 Sakin: %d bölge\n" +
                "🟡 Orta: %d bölge\n" +
                "🟠 Yoğun: %d bölge\n" +
                "🔴 Kritik: %d bölge\n\n" +
                "Ortalama doluluk: %%%d",
                withData, total, empty, moderate, busy, critical, avgPct
        );

        if (critical > 0) {
            List<String> criticalZones = zones.stream()
                    .filter(z -> {
                        Float d = densityMap.get(z.getZoneId());
                        return d != null && d >= 0.85f;
                    })
                    .map(Zone::getZoneName)
                    .toList();
            summary += "\n\n⚠ Kritik bölgeler: " + String.join(", ", criticalZones);
            return ChatbotResponse.withZones(summary, criticalZones);
        }

        return ChatbotResponse.of(summary);
    }

    // ── Yardımcı metodlar ────────────────────────────────────────────────────

    /** Tüm aktif bölgelerin son yoğunluk okumalarını zone_id → density_pct map'i olarak döner. */
    private Map<Long, Float> buildDensityMap() {
        return occupancyReadingRepository.findLatestPerZone()
                .stream()
                .collect(Collectors.toMap(
                        r -> r.getZone().getZoneId(),
                        OccupancyReading::getDensityPct,
                        (a, b) -> a
                ));
    }

    /** Mesajda zone adı geçiyor mu? İlk eşleşen zone'u döner. */
    private Optional<Zone> findZoneInMessage(String normalizedMsg, List<Zone> zones) {
        return zones.stream()
                .filter(z -> {
                    String name = z.getZoneName().toLowerCase(Locale.ROOT)
                            .replace("ı", "i").replace("ö", "o")
                            .replace("ü", "u").replace("ş", "s")
                            .replace("ç", "c").replace("ğ", "g");
                    return normalizedMsg.contains(name);
                })
                .findFirst();
    }

    /** Mesajdan ZoneType filtresi çıkar (lounge, kapı, güvenlik...). */
    private ZoneType detectZoneTypeFilter(String lower) {
        if (containsAny(lower, "lounge", "bekleme salonu", "salon")) return ZoneType.LOUNGE;
        if (containsAny(lower, "kapi", "gate"))                        return ZoneType.GATE;
        if (containsAny(lower, "guvenlik", "security"))                return ZoneType.SECURITY;
        if (containsAny(lower, "checkin", "check-in", "check in"))     return ZoneType.CHECKIN;
        return null;
    }

    /** keywords'lerden herhangi birini içeriyor mu? */
    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /** Uçuş durumu enum adını Türkçeye çevirir. */
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
