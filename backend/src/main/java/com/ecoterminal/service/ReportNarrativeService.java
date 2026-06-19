package com.ecoterminal.service;

import com.ecoterminal.model.dto.ReportContent;
import com.ecoterminal.model.dto.ReportContent.DataPoint;
import com.ecoterminal.model.dto.ReportContent.ReportSection;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analitik metin raporu üretimi.
 *
 * Mimari: veri toplama (collect*) ve metin üretme (build*Narrative) ayrıdır.
 * build* metotları LLM çağrısıyla değiştirilebilir — dışarı sızdırmak için
 * yalnızca bu metodun gövdesi değişir, geri kalan sistem aynı kalır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportNarrativeService {

    private final JdbcTemplate jdbc;

    // ─── Tip → rapor yönlendirici ────────────────────────────────────────────

    public ReportContent generateReport(String type, LocalDate startDate, LocalDate endDate) {
        return switch (type.toUpperCase().trim()) {
            case "USER_REGISTRATION"     -> userRegistrationReport(startDate, endDate);
            case "USER_ECO_POINTS"       -> userEcoPointsReport(startDate, endDate);
            case "USER_ACTIVITY"         -> userActivityReport(startDate, endDate);
            case "ENERGY_CONSUMPTION"    -> energyConsumptionReport(startDate, endDate);
            case "ENERGY_SAVINGS"        -> energySavingsReport(startDate, endDate);
            case "ENERGY_HOURLY"         -> energyHourlyReport(startDate, endDate);
            case "OCCUPANCY_GENERAL"     -> occupancyGeneralReport(startDate, endDate);
            case "OCCUPANCY_ZONE_DETAIL" -> occupancyZoneDetailReport(startDate, endDate);
            case "OCCUPANCY_PEAK_HOURS"  -> occupancyPeakHoursReport(startDate, endDate);
            case "AI_ACCURACY"           -> aiAccuracyReport(startDate, endDate);
            case "AI_RISK_DISTRIBUTION"  -> aiRiskDistributionReport(startDate, endDate);
            default -> errorReport("Bilinmeyen Rapor Türü", startDate, endDate,
                    "Geçerli türler: USER_REGISTRATION, USER_ECO_POINTS, USER_ACTIVITY, " +
                    "ENERGY_CONSUMPTION, ENERGY_SAVINGS, ENERGY_HOURLY, " +
                    "OCCUPANCY_GENERAL, OCCUPANCY_ZONE_DETAIL, OCCUPANCY_PEAK_HOURS, " +
                    "AI_ACCURACY, AI_RISK_DISTRIBUTION");
        };
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // KULLANICI RAPORLARI
    // ═══════════════════════════════════════════════════════════════════════════

    // ── USER_REGISTRATION ────────────────────────────────────────────────────

    private record UserRegistrationData(
            long totalUsers, long adminCount, long passengerCount,
            long newInPeriod, long prevNewInPeriod,
            double emailVerifiedRate,
            List<String[]> monthlyBreakdown   // [month, count]
    ) {}

    private UserRegistrationData collectUserRegistrationData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Instant cStart = pStart.minus(days, ChronoUnit.DAYS);

        Map<String, Object> totals = jdbc.queryForMap(
                "SELECT COUNT(*) AS total, " +
                "COUNT(*) FILTER (WHERE role = 'ADMIN') AS admin_cnt, " +
                "COUNT(*) FILTER (WHERE role = 'USER') AS pass_cnt, " +
                "COALESCE(COUNT(*) FILTER (WHERE email_verified = true) * 100.0 " +
                "         / NULLIF(COUNT(*), 0), 0) AS verified_rate " +
                "FROM users");

        Long newIn  = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= ? AND created_at < ?",
                Long.class, ts(pStart), ts(pEnd));
        Long prevIn = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE created_at >= ? AND created_at < ?",
                Long.class, ts(cStart), ts(pStart));

        List<Map<String, Object>> mRows = jdbc.queryForList(
                "SELECT TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYY-MM') AS month, COUNT(*) AS cnt " +
                "FROM users WHERE created_at >= NOW() - INTERVAL '6 months' " +
                "GROUP BY 1 ORDER BY 1");
        var monthly = mRows.stream()
                .map(r -> new String[]{str(r.get("month")), str(r.get("cnt"))})
                .toList();

        return new UserRegistrationData(
                toLong(totals.get("total")), toLong(totals.get("admin_cnt")),
                toLong(totals.get("pass_cnt")),
                newIn != null ? newIn : 0L, prevIn != null ? prevIn : 0L,
                toDouble(totals.get("verified_rate")), monthly);
    }

    /** ← Bu metodun gövdesi LLM çağrısıyla değiştirilebilir. */
    private List<ReportSection> buildUserRegistrationNarrative(UserRegistrationData d) {
        var sections = new ArrayList<ReportSection>();

        // 1. Genel değerlendirme
        String trend = d.prevNewInPeriod > 0
                ? changePct(d.newInPeriod, d.prevNewInPeriod)
                : "ilk dönem verisi";
        String verifiedComment = d.emailVerifiedRate >= 80
                ? "Bu oran güvenli bir seviyededir."
                : d.emailVerifiedRate >= 50
                    ? "Email doğrulama oranının artırılması önerilir."
                    : "Doğrulama oranı düşüktür; kullanıcılar bilgilendirme e-postasıyla teşvik edilebilir.";

        sections.add(new ReportSection("Genel Değerlendirme",
                List.of(
                    String.format("Bu dönemde sisteme %d yeni kullanıcı kaydoldu (önceki döneme göre %s). " +
                            "Sistemdeki toplam kullanıcı sayısı %d'a ulaştı.",
                            d.newInPeriod, trend, d.totalUsers),
                    String.format("Kullanıcıların %s yönetici (%d kişi), %s yolcu (%d kişi) rolünde bulunmaktadır.",
                            pctStr(d.adminCount, d.totalUsers), d.adminCount,
                            pctStr(d.passengerCount, d.totalUsers), d.passengerCount),
                    String.format("Kayıtlı kullanıcıların %s'si email doğrulamasını tamamlamıştır. %s",
                            pctStr(Math.round(d.emailVerifiedRate), 100L), verifiedComment)
                ),
                List.of(
                    new DataPoint("Toplam Kullanıcı", str(d.totalUsers)),
                    new DataPoint("Dönemde Yeni Kayıt", str(d.newInPeriod)),
                    new DataPoint("Önceki Dönem Yeni Kayıt", str(d.prevNewInPeriod)),
                    new DataPoint("Yönetici", d.adminCount + " (" + pctStr(d.adminCount, d.totalUsers) + ")"),
                    new DataPoint("Yolcu", d.passengerCount + " (" + pctStr(d.passengerCount, d.totalUsers) + ")"),
                    new DataPoint("Email Doğrulama Oranı", fmt1(d.emailVerifiedRate) + "%")
                )));

        // 2. Aylık trend
        if (!d.monthlyBreakdown.isEmpty()) {
            var dp = d.monthlyBreakdown.stream()
                    .map(m -> new DataPoint(m[0], m[1] + " kayıt"))
                    .toList();
            long maxMonth = d.monthlyBreakdown.stream()
                    .mapToLong(m -> parseLong(m[1])).max().orElse(0L);
            String trendText = d.monthlyBreakdown.size() >= 2
                    ? String.format("Son 6 ayda en yüksek aylık kayıt %d olarak gerçekleşti.", maxMonth)
                    : "Aylık kayıt verisi yalnızca bu dönem için mevcuttur.";
            sections.add(new ReportSection("Aylık Kayıt Trendi",
                    List.of(trendText), dp));
        }
        return sections;
    }

    private ReportContent userRegistrationReport(LocalDate start, LocalDate end) {
        try {
            var d = collectUserRegistrationData(start, end);
            var sections = buildUserRegistrationNarrative(d);
            return new ReportContent("Kullanıcı Kayıt Analizi", periodLabel(start, end), nowLabel(),
                    sections,
                    String.format("Dönemde %d yeni kayıt; toplam %d kullanıcı, %s email doğrulama.",
                            d.newInPeriod(), d.totalUsers(), fmt1(d.emailVerifiedRate()) + "%"));
        } catch (Exception e) {
            log.warn("USER_REGISTRATION raporu hatası: {}", e.getMessage());
            return errorReport("Kullanıcı Kayıt Analizi", start, end, e.getMessage());
        }
    }

    // ── USER_ECO_POINTS ──────────────────────────────────────────────────────

    private record EcoPointsData(
            long totalEarned, long totalSpent, long earnCount, long spendCount,
            long earnerCount, long walletCount, long passengerCount,
            List<String[]> topSpendCategories   // [description, cnt, pts]
    ) {}

    private EcoPointsData collectEcoPointsData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));

        Map<String, Object> stats;
        try {
            stats = jdbc.queryForMap(
                    "SELECT COALESCE(SUM(CASE WHEN trans_type='EARN' THEN amount ELSE 0 END),0) AS earned," +
                    "COALESCE(SUM(CASE WHEN trans_type='SPEND' THEN amount ELSE 0 END),0) AS spent," +
                    "COUNT(CASE WHEN trans_type='EARN' THEN 1 END) AS earn_cnt," +
                    "COUNT(CASE WHEN trans_type='SPEND' THEN 1 END) AS spend_cnt " +
                    "FROM transaction_history WHERE created_at >= ? AND created_at < ?",
                    ts(pStart), ts(pEnd));
        } catch (Exception e) {
            stats = Map.of("earned",0,"spent",0,"earn_cnt",0,"spend_cnt",0);
        }

        Long earnerCount = 0L; Long walletCount = 1L;
        try {
            earnerCount = jdbc.queryForObject(
                    "SELECT COUNT(DISTINCT wallet_id) FROM transaction_history " +
                    "WHERE trans_type='EARN' AND created_at >= ? AND created_at < ?",
                    Long.class, ts(pStart), ts(pEnd));
            walletCount = jdbc.queryForObject("SELECT COUNT(*) FROM eco_wallets", Long.class);
        } catch (Exception e) { log.warn("earnerCount sorgusu: {}", e.getMessage()); }

        Long passengerCount = 0L;
        try { passengerCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE role='USER'", Long.class); }
        catch (Exception e) { log.warn("passengerCount: {}", e.getMessage()); }

        List<String[]> topSpend = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT COALESCE(description,'(Açıklama yok)') AS desc, " +
                    "COUNT(*) AS cnt, SUM(amount) AS pts " +
                    "FROM transaction_history WHERE trans_type='SPEND' " +
                    "AND created_at >= ? AND created_at < ? " +
                    "GROUP BY description ORDER BY cnt DESC LIMIT 5",
                    ts(pStart), ts(pEnd));
            topSpend = rows.stream()
                    .map(r -> new String[]{str(r.get("desc")), str(r.get("cnt")), str(r.get("pts"))})
                    .toList();
        } catch (Exception e) { log.warn("topSpend: {}", e.getMessage()); }

        return new EcoPointsData(
                toLong(stats.get("earned")), toLong(stats.get("spent")),
                toLong(stats.get("earn_cnt")), toLong(stats.get("spend_cnt")),
                earnerCount != null ? earnerCount : 0L,
                walletCount != null && walletCount > 0 ? walletCount : 1L,
                passengerCount != null ? passengerCount : 0L,
                topSpend);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildEcoPointsNarrative(EcoPointsData d) {
        var sections = new ArrayList<ReportSection>();
        double earnerRate = d.walletCount > 0 ? d.earnerCount * 100.0 / d.walletCount : 0.0;

        sections.add(new ReportSection("Eko Puan Genel Durumu",
                List.of(
                    String.format("Dönemde toplam %s puan kazanıldı (%d EARN işlemi), " +
                            "%s puan harcandı (%d SPEND işlemi). " +
                            "Net birikim: %s puan.",
                            fmt0(d.totalEarned), d.earnCount,
                            fmt0(d.totalSpent),  d.spendCount,
                            fmt0(d.totalEarned - d.totalSpent)),
                    String.format("Aktif cüzdanların %s'si bu dönemde puan kazandı (%d / %d cüzdan)." +
                            (earnerRate < 30 ? " Katılım oranı düşük; puan kazanma yolları yolculara hatırlatılabilir." : ""),
                            pctStr(d.earnerCount, d.walletCount), d.earnerCount, d.walletCount)
                ),
                List.of(
                    new DataPoint("Toplam Kazanılan", fmt0(d.totalEarned) + " puan"),
                    new DataPoint("Toplam Harcanan",  fmt0(d.totalSpent)  + " puan"),
                    new DataPoint("EARN İşlemi",      str(d.earnCount)),
                    new DataPoint("SPEND İşlemi",     str(d.spendCount)),
                    new DataPoint("Aktif Kazanan Cüzdan", d.earnerCount + " / " + d.walletCount)
                )));

        if (!d.topSpendCategories.isEmpty()) {
            long totalSpendTx = d.topSpendCategories.stream().mapToLong(r -> parseLong(r[1])).sum();
            var bullets = new ArrayList<String>();
            bullets.add("En çok tercih edilen ödüller (harcama açıklamasına göre):");
            for (var r : d.topSpendCategories) {
                long txCount = parseLong(r[1]);
                bullets.add(String.format("  • %s — %d işlem (%s)",
                        r[0], txCount, pctStr(txCount, Math.max(totalSpendTx, 1))));
            }
            var dp = d.topSpendCategories.stream()
                    .map(r -> new DataPoint(r[0], r[1] + " işlem / " + fmt0(parseLong(r[2])) + " pt"))
                    .toList();
            sections.add(new ReportSection("Harcama Kategorileri",
                    bullets.size() == 1
                            ? List.of("Bu dönemde SPEND işlemi bulunmamaktadır.")
                            : bullets,
                    dp));
        }
        return sections;
    }

    private ReportContent userEcoPointsReport(LocalDate start, LocalDate end) {
        try {
            var d = collectEcoPointsData(start, end);
            return new ReportContent("Eko Puan Kullanım Raporu", periodLabel(start, end), nowLabel(),
                    buildEcoPointsNarrative(d),
                    String.format("%s puan kazanıldı, %s harcandı; %d aktif cüzdan.",
                            fmt0(d.totalEarned()), fmt0(d.totalSpent()), d.earnerCount()));
        } catch (Exception e) {
            log.warn("USER_ECO_POINTS hatası: {}", e.getMessage());
            return errorReport("Eko Puan Kullanım Raporu", start, end, e.getMessage());
        }
    }

    // ── USER_ACTIVITY ────────────────────────────────────────────────────────

    private record UserActivityData(
            long passengerCount, long activeEarnerCount, long walletCount,
            long repeatEarnerCount, long routeCompletions,
            long earnTransactions, long spendTransactions
    ) {}

    private UserActivityData collectUserActivityData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));

        Long pass = safeQueryLong("SELECT COUNT(*) FROM users WHERE role='USER'");
        Long active = safeQueryLong(
                "SELECT COUNT(DISTINCT wallet_id) FROM transaction_history " +
                "WHERE trans_type='EARN' AND created_at>=? AND created_at<?",
                ts(pStart), ts(pEnd));
        Long wallets = safeQueryLong("SELECT COUNT(*) FROM eco_wallets");
        Long repeat  = safeQueryLong(
                "SELECT COUNT(*) FROM (" +
                "SELECT wallet_id FROM transaction_history WHERE trans_type='EARN' " +
                "GROUP BY wallet_id HAVING COUNT(*)>1) t");
        Long routes = safeQueryLong("SELECT COUNT(*) FROM route_completions");
        Long earnTx = safeQueryLong(
                "SELECT COUNT(*) FROM transaction_history " +
                "WHERE trans_type='EARN' AND created_at>=? AND created_at<?",
                ts(pStart), ts(pEnd));
        Long spendTx = safeQueryLong(
                "SELECT COUNT(*) FROM transaction_history " +
                "WHERE trans_type='SPEND' AND created_at>=? AND created_at<?",
                ts(pStart), ts(pEnd));
        return new UserActivityData(nn(pass), nn(active), Math.max(nn(wallets),1),
                nn(repeat), nn(routes), nn(earnTx), nn(spendTx));
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildUserActivityNarrative(UserActivityData d) {
        double activeRate  = d.walletCount  > 0 ? d.activeEarnerCount  * 100.0 / d.walletCount  : 0.0;
        double repeatRate  = d.walletCount  > 0 ? d.repeatEarnerCount  * 100.0 / d.walletCount  : 0.0;
        String sampleNote  = d.walletCount < 10 ? " (örneklem küçük, yorumda dikkatli olunmalıdır)" : "";

        return List.of(new ReportSection("Kullanıcı Aktivite Özeti",
                List.of(
                    String.format("Dönemde cüzdanların %s'si aktif puan kazandı (%d / %d cüzdan)%s.",
                            pctStr(d.activeEarnerCount, d.walletCount), d.activeEarnerCount, d.walletCount, sampleNote),
                    String.format("Cüzdanların %s'si birden fazla kez puan kazanma işlemi gerçekleştirdi " +
                            "(%d cüzdan), bu oran eko sisteme tekrarlı katılımı göstermektedir%s.",
                            pctStr(d.repeatEarnerCount, d.walletCount), d.repeatEarnerCount, sampleNote),
                    d.routeCompletions > 0
                        ? String.format("Rota öneri sistemi üzerinden %d tamamlama kaydedildi.", d.routeCompletions)
                        : "Rota tamamlama henüz kaydedilmemiştir.",
                    String.format("Dönemde %d EARN ve %d SPEND işlemi gerçekleşti.",
                            d.earnTransactions, d.spendTransactions)
                ),
                List.of(
                    new DataPoint("Toplam Yolcu",     str(d.passengerCount)),
                    new DataPoint("Aktif Kazanan",    d.activeEarnerCount + " (" + fmt1(activeRate) + "%)"),
                    new DataPoint("Tekrarlı Kazanan", d.repeatEarnerCount + " (" + fmt1(repeatRate) + "%)"),
                    new DataPoint("Rota Tamamlama",   str(d.routeCompletions)),
                    new DataPoint("EARN İşlemi",      str(d.earnTransactions)),
                    new DataPoint("SPEND İşlemi",     str(d.spendTransactions))
                )));
    }

    private ReportContent userActivityReport(LocalDate start, LocalDate end) {
        try {
            var d = collectUserActivityData(start, end);
            return new ReportContent("Kullanıcı Aktiflik Raporu", periodLabel(start, end), nowLabel(),
                    buildUserActivityNarrative(d),
                    String.format("%d aktif kazanan, %d tekrarlı, %d rota tamamlama.",
                            d.activeEarnerCount(), d.repeatEarnerCount(), d.routeCompletions()));
        } catch (Exception e) {
            log.warn("USER_ACTIVITY hatası: {}", e.getMessage());
            return errorReport("Kullanıcı Aktiflik Raporu", start, end, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENERJİ RAPORLARI
    // ═══════════════════════════════════════════════════════════════════════════

    // ── ENERGY_CONSUMPTION ───────────────────────────────────────────────────

    private record EnergyConsumptionData(
            double totalKwh, double prevTotalKwh,
            double avgTemp, double avgLux,
            List<String[]> topZones,      // [name, kwh]
            int dataZoneCount, int totalZoneCount
    ) {}

    private EnergyConsumptionData collectEnergyConsumptionData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Instant cStart = pStart.minus(days, ChronoUnit.DAYS);

        Double totalKwh = safeQueryDouble(
                "SELECT COALESCE(SUM(energy_kwh),0) FROM environmental_metrics " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(pStart), ts(pEnd));
        Double prevKwh  = safeQueryDouble(
                "SELECT COALESCE(SUM(energy_kwh),0) FROM environmental_metrics " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(cStart), ts(pStart));
        Double avgTemp  = safeQueryDouble(
                "SELECT COALESCE(AVG(temperature),0) FROM environmental_metrics " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(pStart), ts(pEnd));
        Double avgLux   = safeQueryDouble(
                "SELECT COALESCE(AVG(lighting_level),0) FROM environmental_metrics " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(pStart), ts(pEnd));

        List<String[]> topZones = List.of();
        int dataZones = 0;
        try {
            var rows = jdbc.queryForList(
                    "SELECT z.zone_name, ROUND(SUM(em.energy_kwh)::numeric,1) AS total_kwh " +
                    "FROM environmental_metrics em JOIN zones z ON em.zone_id=z.zone_id " +
                    "WHERE em.recorded_at>=? AND em.recorded_at<? " +
                    "GROUP BY z.zone_name ORDER BY total_kwh DESC LIMIT 5",
                    ts(pStart), ts(pEnd));
            topZones  = rows.stream().map(r -> new String[]{str(r.get("zone_name")), str(r.get("total_kwh"))}).toList();
            dataZones = rows.size();
        } catch (Exception e) { log.warn("topZones enerji: {}", e.getMessage()); }

        Long totalZones = safeQueryLong("SELECT COUNT(*) FROM zones WHERE is_active=true");

        return new EnergyConsumptionData(
                toDouble(totalKwh), toDouble(prevKwh), toDouble(avgTemp), toDouble(avgLux),
                topZones, dataZones, (int) nn(totalZones));
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildEnergyConsumptionNarrative(EnergyConsumptionData d) {
        var sections = new ArrayList<ReportSection>();

        String delta = d.prevTotalKwh > 0 ? changePct(d.totalKwh, d.prevTotalKwh) : "ilk dönem verisi";
        String deltaComment = d.prevTotalKwh > 0
                ? ((d.totalKwh < d.prevTotalKwh) ? " Bu düşüş enerji verimliliğine olumlu katkı sağlamaktadır."
                    : " Tüketim artışı incelenmelidir.")
                : "";

        sections.add(new ReportSection("Enerji Tüketim Özeti",
                List.of(
                    String.format("Seçili dönemde terminalde toplam %s kWh enerji tüketildi " +
                            "(önceki döneme göre %s).%s",
                            fmt1(d.totalKwh), delta, deltaComment),
                    String.format("Dönem boyunca ortalama ortam sıcaklığı %.1f°C, " +
                            "ortalama aydınlatma düzeyi %.0f lux olarak ölçüldü.",
                            d.avgTemp, d.avgLux),
                    d.dataZoneCount < d.totalZoneCount
                        ? String.format("Enerji verisi %d aktif zone'dan yalnızca %d'inde mevcuttur; " +
                                "geri kalan %d zone'da sensör verisi bulunmamaktadır.",
                                d.totalZoneCount, d.dataZoneCount, d.totalZoneCount - d.dataZoneCount)
                        : String.format("Tüm %d aktif zone'da enerji verisi mevcuttur.", d.totalZoneCount)
                ),
                List.of(
                    new DataPoint("Toplam Tüketim",        fmt1(d.totalKwh) + " kWh"),
                    new DataPoint("Önceki Dönem",          fmt1(d.prevTotalKwh) + " kWh"),
                    new DataPoint("Ort. Sıcaklık",         fmt1(d.avgTemp) + "°C"),
                    new DataPoint("Ort. Aydınlatma",       fmt1(d.avgLux) + " lux"),
                    new DataPoint("Veri Bulunan Zone",      d.dataZoneCount + " / " + d.totalZoneCount)
                )));

        if (!d.topZones.isEmpty()) {
            double maxKwh = parseDouble(d.topZones.get(0)[1]);
            var bullets = new ArrayList<String>();
            bullets.add(String.format("En yüksek tüketim %s zone'unda gerçekleşti (%s kWh).",
                    d.topZones.get(0)[0], d.topZones.get(0)[1]));
            if (d.topZones.size() > 1) {
                bullets.add("Yüksek tüketimli bölgeler için optimizasyon planı önceliklendirilmelidir.");
            }
            var dp = d.topZones.stream()
                    .map(z -> new DataPoint(z[0], z[1] + " kWh"))
                    .toList();
            sections.add(new ReportSection("Zone Bazlı Tüketim (Top 5)", bullets, dp));
        }
        return sections;
    }

    private ReportContent energyConsumptionReport(LocalDate start, LocalDate end) {
        try {
            var d = collectEnergyConsumptionData(start, end);
            return new ReportContent("Enerji Tüketim Raporu", periodLabel(start, end), nowLabel(),
                    buildEnergyConsumptionNarrative(d),
                    String.format("Toplam %s kWh (%s); ort. %.1f°C, %.0f lux.",
                            fmt1(d.totalKwh()), changePct(d.totalKwh(), d.prevTotalKwh()),
                            d.avgTemp(), d.avgLux()));
        } catch (Exception e) {
            log.warn("ENERGY_CONSUMPTION hatası: {}", e.getMessage());
            return errorReport("Enerji Tüketim Raporu", start, end, e.getMessage());
        }
    }

    // ── ENERGY_SAVINGS ───────────────────────────────────────────────────────

    private record SavingsOpportunityEntry(String zoneName, double avgKwh, double avgDensityPct) {}

    private record EnergySavingsData(List<SavingsOpportunityEntry> opportunities) {}

    private EnergySavingsData collectEnergySavingsData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        List<SavingsOpportunityEntry> ops = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT z.zone_name, " +
                    "ROUND(AVG(em.energy_kwh)::numeric,2) AS avg_kwh, " +
                    "COALESCE(ROUND((AVG(oc.density_pct)*100)::numeric,1), 0) AS avg_density " +
                    "FROM environmental_metrics em JOIN zones z ON em.zone_id=z.zone_id " +
                    "LEFT JOIN (SELECT zone_id, AVG(density_pct) AS density_pct " +
                    "           FROM occupancy_readings WHERE recorded_at>=? AND recorded_at<? " +
                    "           GROUP BY zone_id) oc ON oc.zone_id=z.zone_id " +
                    "WHERE em.recorded_at>=? AND em.recorded_at<? " +
                    "GROUP BY z.zone_name " +
                    "HAVING AVG(em.energy_kwh) > 10 AND COALESCE(AVG(oc.density_pct),1.0) < 0.5 " +
                    "ORDER BY avg_kwh DESC",
                    ts(pStart), ts(pEnd), ts(pStart), ts(pEnd));
            ops = rows.stream()
                    .map(r -> new SavingsOpportunityEntry(
                            str(r.get("zone_name")), toDouble(r.get("avg_kwh")), toDouble(r.get("avg_density"))))
                    .toList();
        } catch (Exception e) { log.warn("savings opportunities: {}", e.getMessage()); }
        return new EnergySavingsData(ops);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildEnergySavingsNarrative(EnergySavingsData d) {
        if (d.opportunities.isEmpty()) {
            return List.of(new ReportSection("Tasarruf Analizi",
                    List.of("Seçili dönemde yüksek enerji + düşük doluluk eşleşmesi bulunamadı. " +
                            "Bu durum enerji kullanımının dolulukla orantılı olduğunu gösterir."),
                    List.of()));
        }

        var bullets = new ArrayList<String>();
        bullets.add(String.format("%d zone tasarruf adayı olarak tespit edildi " +
                "(ortalama enerji > 10 kWh/okuma ve doluluk < %%50):", d.opportunities.size()));
        for (var op : d.opportunities) {
            double potential = (0.50 - op.avgDensityPct() / 100.0) * 40.0; // rough 40% max savings
            bullets.add(String.format(
                    "  • %s — %.1f kWh/okuma, %%%s doluluk → " +
                    "aydınlatma/iklimlendirme optimizasyonuyla ~%%%s tasarruf potansiyeli.",
                    op.zoneName(), op.avgKwh(), fmt1(op.avgDensityPct()), fmt1(Math.max(0, potential))));
        }
        bullets.add("Bu zone'larda hareket sensörlü akıllı aydınlatma ve bölgesel klima kontrolü " +
                "devreye alınabilir.");

        var dp = d.opportunities.stream()
                .map(op -> new DataPoint(op.zoneName(),
                        fmt1(op.avgKwh()) + " kWh | %" + fmt1(op.avgDensityPct()) + " doluluk"))
                .toList();
        return List.of(new ReportSection("Tasarruf Fırsatları", bullets, dp));
    }

    private ReportContent energySavingsReport(LocalDate start, LocalDate end) {
        try {
            var d = collectEnergySavingsData(start, end);
            return new ReportContent("Enerji Tasarruf Analizi", periodLabel(start, end), nowLabel(),
                    buildEnergySavingsNarrative(d),
                    d.opportunities.isEmpty()
                        ? "Tasarruf adayı zone bulunamadı."
                        : d.opportunities.size() + " zone tasarruf adayı tespit edildi.");
        } catch (Exception e) {
            log.warn("ENERGY_SAVINGS hatası: {}", e.getMessage());
            return errorReport("Enerji Tasarruf Analizi", start, end, e.getMessage());
        }
    }

    // ── ENERGY_HOURLY ────────────────────────────────────────────────────────

    private record HourlyEnergyEntry(int hour, double avgKwh) {}
    private record EnergyHourlyData(List<HourlyEnergyEntry> hourly) {}

    private EnergyHourlyData collectEnergyHourlyData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        List<HourlyEnergyEntry> entries = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT EXTRACT(HOUR FROM recorded_at AT TIME ZONE 'UTC')::int AS hr, " +
                    "ROUND(AVG(energy_kwh)::numeric,2) AS avg_kwh " +
                    "FROM environmental_metrics WHERE recorded_at>=? AND recorded_at<? " +
                    "GROUP BY hr ORDER BY hr",
                    ts(pStart), ts(pEnd));
            entries = rows.stream()
                    .map(r -> new HourlyEnergyEntry((int) toLong(r.get("hr")), toDouble(r.get("avg_kwh"))))
                    .toList();
        } catch (Exception e) { log.warn("hourly energy: {}", e.getMessage()); }
        return new EnergyHourlyData(entries);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildEnergyHourlyNarrative(EnergyHourlyData d) {
        if (d.hourly().isEmpty()) {
            return List.of(new ReportSection("Saatlik Tüketim",
                    List.of("Seçili dönem için saatlik enerji verisi bulunamadı."), List.of()));
        }
        var sorted = d.hourly().stream()
                .sorted(Comparator.comparingDouble(HourlyEnergyEntry::avgKwh).reversed())
                .toList();
        var top3 = sorted.stream().limit(3).toList();
        var low3 = sorted.stream().skip(Math.max(0, sorted.size() - 3)).toList();

        List<DataPoint> dp = d.hourly().stream()
                .map(h -> new DataPoint(String.format("%02d:00", h.hour()), fmt1(h.avgKwh()) + " kWh"))
                .toList();

        String peakHours = top3.stream()
                .map(h -> String.format("%02d:00 (%.1f kWh)", h.hour(), h.avgKwh()))
                .collect(Collectors.joining(", "));
        String lowHours  = low3.stream()
                .map(h -> String.format("%02d:00 (%.1f kWh)", h.hour(), h.avgKwh()))
                .collect(Collectors.joining(", "));

        return List.of(new ReportSection("Saatlik Tüketim Analizi",
                List.of(
                    String.format("En yüksek enerji tüketimi %s saatlerinde gerçekleşmektedir. " +
                            "Bu saatlerde bakım ve optimizasyon çalışmalarının planlanması önerilmez.", peakHours),
                    String.format("En düşük tüketim %s saatlerinde görülmektedir; " +
                            "bu zaman dilimleri bakım penceresi için idealdir.", lowHours)
                ), dp));
    }

    private ReportContent energyHourlyReport(LocalDate start, LocalDate end) {
        try {
            var d = collectEnergyHourlyData(start, end);
            return new ReportContent("Saatlik Tüketim Raporu", periodLabel(start, end), nowLabel(),
                    buildEnergyHourlyNarrative(d),
                    d.hourly().isEmpty() ? "Veri bulunamadı."
                        : d.hourly().size() + " saatlik dilim analiz edildi.");
        } catch (Exception e) {
            log.warn("ENERGY_HOURLY hatası: {}", e.getMessage());
            return errorReport("Saatlik Tüketim Raporu", start, end, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // YOĞUNLUK RAPORLARI
    // ═══════════════════════════════════════════════════════════════════════════

    // ── OCCUPANCY_GENERAL ────────────────────────────────────────────────────

    private record OccupancyGeneralData(
            double avgDensity, double prevAvgDensity,
            long criticalReadings, long totalReadings,
            int peakHour, double peakHourDensity,
            List<String[]> top3Zones    // [name, avgPct]
    ) {}

    private OccupancyGeneralData collectOccupancyGeneralData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        Instant cStart = pStart.minus(days, ChronoUnit.DAYS);

        Double avg  = safeQueryDouble(
                "SELECT COALESCE(AVG(density_pct)*100, 0) FROM occupancy_readings " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(pStart), ts(pEnd));
        Double prev = safeQueryDouble(
                "SELECT COALESCE(AVG(density_pct)*100, 0) FROM occupancy_readings " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(cStart), ts(pStart));
        Long   crit = safeQueryLong(
                "SELECT COUNT(*) FROM occupancy_readings WHERE density_pct>=0.85 " +
                "AND recorded_at>=? AND recorded_at<?", ts(pStart), ts(pEnd));
        Long   total = safeQueryLong(
                "SELECT COUNT(*) FROM occupancy_readings " +
                "WHERE recorded_at>=? AND recorded_at<?", ts(pStart), ts(pEnd));

        // Peak hour
        Map<String, Object> peakRow = new HashMap<>();
        try {
            var rows = jdbc.queryForList(
                    "SELECT EXTRACT(HOUR FROM recorded_at AT TIME ZONE 'UTC')::int AS hr, " +
                    "AVG(density_pct)*100 AS avg_d FROM occupancy_readings " +
                    "WHERE recorded_at>=? AND recorded_at<? GROUP BY hr ORDER BY avg_d DESC LIMIT 1",
                    ts(pStart), ts(pEnd));
            if (!rows.isEmpty()) peakRow = rows.get(0);
        } catch (Exception e) { log.warn("peak hour: {}", e.getMessage()); }

        // Top 3 zones
        List<String[]> top3 = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT z.zone_name, ROUND((AVG(or2.density_pct)*100)::numeric,1) AS avg_pct " +
                    "FROM occupancy_readings or2 JOIN zones z ON or2.zone_id=z.zone_id " +
                    "WHERE or2.recorded_at>=? AND or2.recorded_at<? " +
                    "GROUP BY z.zone_name ORDER BY avg_pct DESC LIMIT 3",
                    ts(pStart), ts(pEnd));
            top3 = rows.stream().map(r -> new String[]{str(r.get("zone_name")), str(r.get("avg_pct"))}).toList();
        } catch (Exception e) { log.warn("top3 zones: {}", e.getMessage()); }

        return new OccupancyGeneralData(
                toDouble(avg), toDouble(prev), nn(crit), nn(total),
                (int) toLong(peakRow.getOrDefault("hr", 12L)),
                toDouble(peakRow.getOrDefault("avg_d", 0.0)),
                top3);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildOccupancyGeneralNarrative(OccupancyGeneralData d) {
        var sections = new ArrayList<ReportSection>();
        String delta = d.prevAvgDensity > 0 ? changePct(d.avgDensity, d.prevAvgDensity) : "ilk dönem verisi";
        String densityComment = d.avgDensity >= 70 ? "Terminal kapasitesine yakın çalışmaktadır."
                : d.avgDensity >= 50 ? "Doluluk dengeli bir seviyededir."
                : "Terminal kapasitesinin altında seyretmiştir.";
        double critRate = d.totalReadings > 0 ? d.criticalReadings * 100.0 / d.totalReadings : 0.0;

        sections.add(new ReportSection("Yoğunluk Genel Değerlendirme",
                List.of(
                    String.format("Seçili dönemde terminal genelinde ortalama doluluk %%%s olarak " +
                            "gerçekleşti (önceki döneme göre %s). %s",
                            fmt1(d.avgDensity), delta, densityComment),
                    String.format("Toplam %d okumadan %d'i (%s) kritik eşik (%%85) üzerindeydi. " +
                            "%s",
                            d.totalReadings, d.criticalReadings, pctStr(d.criticalReadings, d.totalReadings),
                            d.criticalReadings > 0
                                ? "Bu dönemlerde kalabalık yönlendirmesi devreye alındı."
                                : "Kritik seviyeye ulaşılmadı."),
                    String.format("En yüksek doluluk %02d:00 – %02d:00 arasında %%%s ile gözlemlendi.",
                            d.peakHour, (d.peakHour + 1) % 24, fmt1(d.peakHourDensity))
                ),
                List.of(
                    new DataPoint("Ort. Doluluk",       fmt1(d.avgDensity) + "%"),
                    new DataPoint("Önceki Dönem Ort.",   fmt1(d.prevAvgDensity) + "%"),
                    new DataPoint("Kritik Okuma",        d.criticalReadings + " (" + fmt1(critRate) + "%)"),
                    new DataPoint("Toplam Okuma",        str(d.totalReadings)),
                    new DataPoint("Peak Saat",           String.format("%02d:00 (%s%%)", d.peakHour, fmt1(d.peakHourDensity)))
                )));

        if (!d.top3Zones.isEmpty()) {
            var bullets = new ArrayList<String>();
            bullets.add("En yoğun bölgeler:");
            for (int i = 0; i < d.top3Zones.size(); i++) {
                bullets.add(String.format("  %d. %s — ortalama %%%s doluluk",
                        i + 1, d.top3Zones.get(i)[0], d.top3Zones.get(i)[1]));
            }
            bullets.add("Bu bölgeler için alternatif rota yönlendirmeleri önceliklendirilmelidir.");
            var dp = d.top3Zones.stream()
                    .map(z -> new DataPoint(z[0], "%" + z[1]))
                    .toList();
            sections.add(new ReportSection("En Yoğun 3 Bölge", bullets, dp));
        }
        return sections;
    }

    private ReportContent occupancyGeneralReport(LocalDate start, LocalDate end) {
        try {
            var d = collectOccupancyGeneralData(start, end);
            return new ReportContent("Yoğunluk Genel Raporu", periodLabel(start, end), nowLabel(),
                    buildOccupancyGeneralNarrative(d),
                    String.format("Ort. %%%s doluluk; %d kritik okuma; pik saat %02d:00.",
                            fmt1(d.avgDensity()), d.criticalReadings(), d.peakHour()));
        } catch (Exception e) {
            log.warn("OCCUPANCY_GENERAL hatası: {}", e.getMessage());
            return errorReport("Yoğunluk Genel Raporu", start, end, e.getMessage());
        }
    }

    // ── OCCUPANCY_ZONE_DETAIL ────────────────────────────────────────────────

    private record ZoneDetail(String name, double avgPct, double maxPct, double minPct, long critCount, long readings) {}
    private record OccupancyZoneData(List<ZoneDetail> zones) {}

    private OccupancyZoneData collectOccupancyZoneData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        List<ZoneDetail> zones = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT z.zone_name, " +
                    "ROUND((AVG(or2.density_pct)*100)::numeric,1) AS avg_pct, " +
                    "ROUND((MAX(or2.density_pct)*100)::numeric,1) AS max_pct, " +
                    "ROUND((MIN(or2.density_pct)*100)::numeric,1) AS min_pct, " +
                    "COUNT(*) FILTER (WHERE or2.density_pct>=0.85) AS crit_cnt, " +
                    "COUNT(*) AS total " +
                    "FROM occupancy_readings or2 JOIN zones z ON or2.zone_id=z.zone_id " +
                    "WHERE or2.recorded_at>=? AND or2.recorded_at<? " +
                    "GROUP BY z.zone_name ORDER BY avg_pct DESC",
                    ts(pStart), ts(pEnd));
            zones = rows.stream()
                    .map(r -> new ZoneDetail(str(r.get("zone_name")),
                            toDouble(r.get("avg_pct")), toDouble(r.get("max_pct")),
                            toDouble(r.get("min_pct")), toLong(r.get("crit_cnt")),
                            toLong(r.get("total"))))
                    .toList();
        } catch (Exception e) { log.warn("zone detail: {}", e.getMessage()); }
        return new OccupancyZoneData(zones);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildOccupancyZoneNarrative(OccupancyZoneData d) {
        if (d.zones.isEmpty()) {
            return List.of(new ReportSection("Zone Analizi",
                    List.of("Seçili dönem için zone bazlı yoğunluk verisi bulunamadı."), List.of()));
        }
        long highOcc  = d.zones.stream().filter(z -> z.avgPct() >= 70).count();
        long critZones = d.zones.stream().filter(z -> z.critCount() > 0).count();
        var top    = d.zones.get(0);
        var bottom = d.zones.get(d.zones.size() - 1);
        var dp = d.zones.stream()
                .map(z -> new DataPoint(z.name(),
                        String.format("ort.%%%s max:%%%s kritik:%d", fmt1(z.avgPct()), fmt1(z.maxPct()), z.critCount())))
                .toList();
        return List.of(new ReportSection("Zone Bazlı Doluluk Dökümü",
                List.of(
                    String.format("%d zone analiz edildi. %d zone ortalama %%%70 ve üzerinde doluluk gösterdi; " +
                            "%d zone'da kritik (%85 üzeri) doluluk yaşandı.",
                            d.zones.size(), highOcc, critZones),
                    String.format("En yoğun zone: %s (ort. %%%s, max %%%s). " +
                            "En sakin zone: %s (ort. %%%s).",
                            top.name(), fmt1(top.avgPct()), fmt1(top.maxPct()),
                            bottom.name(), fmt1(bottom.avgPct()))
                ), dp));
    }

    private ReportContent occupancyZoneDetailReport(LocalDate start, LocalDate end) {
        try {
            var d = collectOccupancyZoneData(start, end);
            return new ReportContent("Zone Bazlı Yoğunluk Raporu", periodLabel(start, end), nowLabel(),
                    buildOccupancyZoneNarrative(d),
                    d.zones.isEmpty() ? "Veri bulunamadı." : d.zones.size() + " zone analiz edildi.");
        } catch (Exception e) {
            log.warn("OCCUPANCY_ZONE_DETAIL hatası: {}", e.getMessage());
            return errorReport("Zone Bazlı Yoğunluk Raporu", start, end, e.getMessage());
        }
    }

    // ── OCCUPANCY_PEAK_HOURS ─────────────────────────────────────────────────

    private record PeakHourEntry(int hour, double avgPct, long critCount) {}
    private record PeakHourDayEntry(String date, double avgPct, long critCount) {}
    private record OccupancyPeakData(List<PeakHourEntry> hourly, List<PeakHourDayEntry> daily) {}

    private OccupancyPeakData collectOccupancyPeakData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        List<PeakHourEntry>    hourly = List.of();
        List<PeakHourDayEntry> daily  = List.of();
        try {
            var hRows = jdbc.queryForList(
                    "SELECT EXTRACT(HOUR FROM recorded_at AT TIME ZONE 'UTC')::int AS hr, " +
                    "ROUND((AVG(density_pct)*100)::numeric,1) AS avg_d, " +
                    "COUNT(*) FILTER (WHERE density_pct>=0.85) AS crit " +
                    "FROM occupancy_readings WHERE recorded_at>=? AND recorded_at<? " +
                    "GROUP BY hr ORDER BY avg_d DESC",
                    ts(pStart), ts(pEnd));
            hourly = hRows.stream()
                    .map(r -> new PeakHourEntry((int)toLong(r.get("hr")), toDouble(r.get("avg_d")), toLong(r.get("crit"))))
                    .toList();
        } catch (Exception e) { log.warn("peak hours: {}", e.getMessage()); }
        try {
            var dRows = jdbc.queryForList(
                    "SELECT TO_CHAR(recorded_at AT TIME ZONE 'UTC','YYYY-MM-DD') AS day, " +
                    "ROUND((AVG(density_pct)*100)::numeric,1) AS avg_d, " +
                    "COUNT(*) FILTER (WHERE density_pct>=0.85) AS crit " +
                    "FROM occupancy_readings WHERE recorded_at>=? AND recorded_at<? " +
                    "GROUP BY day ORDER BY avg_d DESC LIMIT 14",
                    ts(pStart), ts(pEnd));
            daily = dRows.stream()
                    .map(r -> new PeakHourDayEntry(str(r.get("day")), toDouble(r.get("avg_d")), toLong(r.get("crit"))))
                    .toList();
        } catch (Exception e) { log.warn("peak days: {}", e.getMessage()); }
        return new OccupancyPeakData(hourly, daily);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildOccupancyPeakNarrative(OccupancyPeakData d) {
        var sections = new ArrayList<ReportSection>();

        if (!d.hourly().isEmpty()) {
            var top5 = d.hourly().stream().limit(5).toList();
            String peakDesc = top5.stream().limit(3)
                    .map(h -> String.format("%02d:00 (%%%s)", h.hour(), fmt1(h.avgPct())))
                    .collect(Collectors.joining(", "));
            var dp = top5.stream()
                    .map(h -> new DataPoint(String.format("%02d:00", h.hour()),
                            "%" + fmt1(h.avgPct()) + (h.critCount() > 0 ? " | " + h.critCount() + " kritik" : "")))
                    .toList();
            sections.add(new ReportSection("Saatlik Pik Analizi",
                    List.of(
                        String.format("En yoğun saatler: %s. Bu zaman dilimlerinde personel takviyesi " +
                                "ve aktif yönlendirme önerilmektedir.", peakDesc),
                        top5.stream().anyMatch(h -> h.critCount() > 0)
                            ? "Pik saatlerde kritik doluluk gözlemlendi; anlık uyarı sistemi etkin olmalıdır."
                            : "Pik saatlerde kritik eşik aşımı yaşanmadı."
                    ), dp));
        }

        if (!d.daily().isEmpty()) {
            var top = d.daily().get(0);
            var dp = d.daily().stream()
                    .map(day -> new DataPoint(day.date(), "%" + fmt1(day.avgPct()) +
                            (day.critCount() > 0 ? " (" + day.critCount() + " kritik)" : "")))
                    .toList();
            sections.add(new ReportSection("Günlük Yoğunluk Trendi",
                    List.of(String.format("En yoğun gün %s (%%%s ort. doluluk, %d kritik okuma).",
                            top.date(), fmt1(top.avgPct()), top.critCount())),
                    dp));
        }
        if (sections.isEmpty()) {
            sections.add(new ReportSection("Pik Saat Analizi",
                    List.of("Seçili dönem için yoğunluk verisi bulunamadı."), List.of()));
        }
        return sections;
    }

    private ReportContent occupancyPeakHoursReport(LocalDate start, LocalDate end) {
        try {
            var d = collectOccupancyPeakData(start, end);
            return new ReportContent("Pik Saat Analizi", periodLabel(start, end), nowLabel(),
                    buildOccupancyPeakNarrative(d),
                    d.hourly().isEmpty() ? "Veri bulunamadı." :
                        String.format("Peak: %02d:00 (%%%s)", d.hourly().get(0).hour(), fmt1(d.hourly().get(0).avgPct())));
        } catch (Exception e) {
            log.warn("OCCUPANCY_PEAK_HOURS hatası: {}", e.getMessage());
            return errorReport("Pik Saat Analizi", start, end, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // AI RAPORLARI
    // ═══════════════════════════════════════════════════════════════════════════

    // ── AI_ACCURACY ──────────────────────────────────────────────────────────

    private record AiAccuracyData(double mae, double maePct, double correlation, long matchedPairs) {}

    private AiAccuracyData collectAiAccuracyData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        try {
            var row = jdbc.queryForMap(
                    "SELECT COUNT(*) AS mp, " +
                    "ROUND(AVG(ABS(ap.predicted_load - or2.density_pct))::numeric,4) AS mae, " +
                    "COALESCE(ROUND(CORR(ap.predicted_load::float8, or2.density_pct::float8)::numeric,4),0) AS corr " +
                    "FROM ai_predictions ap " +
                    "JOIN LATERAL (SELECT density_pct FROM occupancy_readings oc " +
                    "  WHERE oc.zone_id=ap.zone_id " +
                    "    AND ABS(EXTRACT(EPOCH FROM (oc.recorded_at - ap.forecast_time))) < 300 " +
                    "  ORDER BY ABS(EXTRACT(EPOCH FROM (oc.recorded_at - ap.forecast_time))) LIMIT 1) or2 ON true " +
                    "WHERE ap.generated_at>=? AND ap.generated_at<?",
                    ts(pStart), ts(pEnd));
            double mae = toDouble(row.get("mae"));
            return new AiAccuracyData(mae, Math.round(mae * 10000.0) / 100.0,
                    toDouble(row.get("corr")), toLong(row.get("mp")));
        } catch (Exception e) {
            log.warn("AI accuracy data: {}", e.getMessage());
            return new AiAccuracyData(0, 0, 0, 0);
        }
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildAiAccuracyNarrative(AiAccuracyData d) {
        if (d.matchedPairs() == 0) {
            return List.of(new ReportSection("Model Doğruluğu",
                    List.of("Seçili dönemde tahmin–ölçüm eşleşmesi yapılamadı."), List.of()));
        }
        String corrDesc  = d.correlation() >= 0.7 ? "güçlü pozitif"
                : d.correlation() >= 0.4 ? "orta pozitif"
                : d.correlation() >= 0.0 ? "zayıf pozitif" : "negatif";
        String maeQuality = d.maePct() < 10 ? "yüksek doğruluk seviyesi"
                : d.maePct() < 20 ? "kabul edilebilir doğruluk"
                : "iyileştirme gerektiren doğruluk";

        return List.of(new ReportSection("AI Model Doğruluk Analizi",
                List.of(
                    String.format("AI modeli %d tahmin–gerçek çiftinde değerlendirildi. " +
                            "Ortalama mutlak hata (MAE) %%%s olarak ölçüldü; bu %s anlamına gelir.",
                            d.matchedPairs(), fmt1(d.maePct()), maeQuality),
                    String.format("Tahmin–gerçek korelasyonu %.3f (%s ilişki). " +
                            "Korelasyonun 1.0'a yaklaşması modelin doluluk trendlerini doğru yakaladığını gösterir.",
                            d.correlation(), corrDesc),
                    "Model şu an fallback (ağırlıklı ortalama) tahmini kullanmaktadır. " +
                    "LSTM eğitimi tamamlandığında doğruluk önemli ölçüde artacaktır."
                ),
                List.of(
                    new DataPoint("Eşleşen Çift",   str(d.matchedPairs())),
                    new DataPoint("MAE (ham)",       String.valueOf(d.mae())),
                    new DataPoint("MAE (%)",         fmt1(d.maePct()) + "%"),
                    new DataPoint("Korelasyon",      String.format("%.3f (%s)", d.correlation(), corrDesc)),
                    new DataPoint("Model Kalitesi",  maeQuality)
                )));
    }

    private ReportContent aiAccuracyReport(LocalDate start, LocalDate end) {
        try {
            var d = collectAiAccuracyData(start, end);
            return new ReportContent("AI Tahmin Doğruluk Raporu", periodLabel(start, end), nowLabel(),
                    buildAiAccuracyNarrative(d),
                    String.format("%d çift; MAE %%%s; korelasyon %.3f.",
                            d.matchedPairs(), fmt1(d.maePct()), d.correlation()));
        } catch (Exception e) {
            log.warn("AI_ACCURACY hatası: {}", e.getMessage());
            return errorReport("AI Tahmin Doğruluk Raporu", start, end, e.getMessage());
        }
    }

    // ── AI_RISK_DISTRIBUTION ─────────────────────────────────────────────────

    private record RiskEntry(String zoneName, long highCount) {}
    private record AiRiskData(long total, long high, long medium, long low,
                               List<RiskEntry> topZones, List<String[]> dailyHigh) {}

    private AiRiskData collectAiRiskData(LocalDate start, LocalDate end) {
        Instant pStart = toInstant(start), pEnd = toInstant(end.plusDays(1));
        long total=0,high=0,medium=0,low=0;
        try {
            var rows = jdbc.queryForList(
                    "SELECT risk_level, COUNT(*) AS cnt FROM ai_predictions " +
                    "WHERE generated_at>=? AND generated_at<? GROUP BY risk_level",
                    ts(pStart), ts(pEnd));
            for (var r : rows) {
                long c = toLong(r.get("cnt"));
                total += c;
                switch (str(r.get("risk_level"))) {
                    case "HIGH" -> high = c; case "MEDIUM" -> medium = c; case "LOW" -> low = c;
                }
            }
        } catch (Exception e) { log.warn("risk dist: {}", e.getMessage()); }

        List<RiskEntry> topZones = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT z.zone_name, COUNT(*) AS hc FROM ai_predictions ap " +
                    "JOIN zones z ON ap.zone_id=z.zone_id " +
                    "WHERE ap.risk_level='HIGH' AND ap.generated_at>=? AND ap.generated_at<? " +
                    "GROUP BY z.zone_name ORDER BY hc DESC LIMIT 5",
                    ts(pStart), ts(pEnd));
            topZones = rows.stream()
                    .map(r -> new RiskEntry(str(r.get("zone_name")), toLong(r.get("hc"))))
                    .toList();
        } catch (Exception e) { log.warn("top risky zones: {}", e.getMessage()); }

        List<String[]> dailyHigh = List.of();
        try {
            var rows = jdbc.queryForList(
                    "SELECT TO_CHAR(generated_at AT TIME ZONE 'UTC','YYYY-MM-DD') AS day, COUNT(*) AS cnt " +
                    "FROM ai_predictions WHERE risk_level='HIGH' AND generated_at>=? AND generated_at<? " +
                    "GROUP BY day ORDER BY day",
                    ts(pStart), ts(pEnd));
            dailyHigh = rows.stream()
                    .map(r -> new String[]{str(r.get("day")), str(r.get("cnt"))})
                    .toList();
        } catch (Exception e) { log.warn("daily high: {}", e.getMessage()); }

        return new AiRiskData(total, high, medium, low, topZones, dailyHigh);
    }

    /** ← LLM ile değiştirilebilir. */
    private List<ReportSection> buildAiRiskNarrative(AiRiskData d) {
        var sections = new ArrayList<ReportSection>();
        if (d.total == 0) {
            return List.of(new ReportSection("Risk Dağılımı",
                    List.of("Seçili dönem için AI tahmin verisi bulunamadı."), List.of()));
        }
        long safeTotal = Math.max(d.total, 1);
        double highPct = d.high * 100.0 / safeTotal;
        String highComment = highPct >= 15 ? "Bu oran yüksektir; terminal yönetimi uyarılmalıdır."
                : highPct >= 5 ? "Oran kabul edilebilir; izleme sürdürülmelidir."
                : "Düşük risk oranı ile terminal güvenli seyretmektedir.";

        sections.add(new ReportSection("Risk Dağılımı",
                List.of(
                    String.format("Dönemde %s tahmin üretildi. Dağılım: HIGH %%%s (%d), " +
                            "MEDIUM %%%s (%d), LOW %%%s (%d). %s",
                            fmt0(d.total),
                            fmt1(highPct), d.high,
                            fmt1(d.medium * 100.0 / safeTotal), d.medium,
                            fmt1(d.low * 100.0 / safeTotal), d.low,
                            highComment)
                ),
                List.of(
                    new DataPoint("Toplam Tahmin", fmt0(d.total)),
                    new DataPoint("HIGH",   d.high   + " (" + fmt1(highPct) + "%)"),
                    new DataPoint("MEDIUM", d.medium + " (" + fmt1(d.medium*100.0/safeTotal) + "%)"),
                    new DataPoint("LOW",    d.low    + " (" + fmt1(d.low*100.0/safeTotal) + "%)")
                )));

        if (!d.topZones.isEmpty()) {
            var bullets = new ArrayList<String>();
            bullets.add("En sık HIGH alarm üreten bölgeler:");
            d.topZones.forEach(z -> bullets.add(String.format("  • %s — %d HIGH alarm", z.zoneName(), z.highCount())));
            var dp = d.topZones.stream()
                    .map(z -> new DataPoint(z.zoneName(), z.highCount() + " HIGH alarm"))
                    .toList();
            sections.add(new ReportSection("En Riskli Bölgeler", bullets, dp));
        }

        if (!d.dailyHigh.isEmpty()) {
            long maxDay = d.dailyHigh.stream().mapToLong(r -> parseLong(r[1])).max().orElse(0L);
            String maxDate = d.dailyHigh.stream()
                    .filter(r -> parseLong(r[1]) == maxDay).findFirst().map(r -> r[0]).orElse("—");
            var dp = d.dailyHigh.stream().map(r -> new DataPoint(r[0], r[1] + " HIGH")).toList();
            sections.add(new ReportSection("Günlük HIGH Alarm Trendi",
                    List.of(String.format("En yüksek günlük HIGH alarm %s tarihinde %d olarak gerçekleşti.",
                            maxDate, maxDay)),
                    dp));
        }
        return sections;
    }

    private ReportContent aiRiskDistributionReport(LocalDate start, LocalDate end) {
        try {
            var d = collectAiRiskData(start, end);
            return new ReportContent("AI Risk Dağılım Raporu", periodLabel(start, end), nowLabel(),
                    buildAiRiskNarrative(d),
                    String.format("%s tahmin; HIGH %%%s, MEDIUM %%%s, LOW %%%s.",
                            fmt0(d.total),
                            d.total > 0 ? fmt1(d.high * 100.0 / d.total) : "0",
                            d.total > 0 ? fmt1(d.medium * 100.0 / d.total) : "0",
                            d.total > 0 ? fmt1(d.low * 100.0 / d.total) : "0"));
        } catch (Exception e) {
            log.warn("AI_RISK_DISTRIBUTION hatası: {}", e.getMessage());
            return errorReport("AI Risk Dağılım Raporu", start, end, e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // YARDIMCI METODLAR
    // ═══════════════════════════════════════════════════════════════════════════

    private Instant toInstant(LocalDate d)  { return d.atStartOfDay(ZoneOffset.UTC).toInstant(); }
    private Timestamp ts(Instant i)         { return Timestamp.from(i); }
    private long toLong(Object v)           { if (v==null) return 0L; if (v instanceof Long l) return l; if (v instanceof Number n) return n.longValue(); return 0L; }
    private double toDouble(Object v)       { if (v==null) return 0.0; if (v instanceof Double d) return d; if (v instanceof Number n) return n.doubleValue(); return 0.0; }
    private long   nn(Long v)               { return v != null ? v : 0L; }
    private String str(Object v)            { return v == null ? "—" : String.valueOf(v); }
    private String fmt1(double v)           { return String.format("%.1f", v); }
    private String fmt0(long v)             { return String.format("%,d", v).replace(',', '.'); }
    private double parseDouble(String s)    { try { return Double.parseDouble(s.replace(",",".")); } catch(Exception e) { return 0.0; } }
    private long   parseLong(String s)      { try { return Long.parseLong(s.trim()); } catch(Exception e) { return 0L; } }

    private String pctStr(long num, long denom) {
        if (denom <= 0) return "0.0%";
        return fmt1(num * 100.0 / denom) + "%";
    }

    private String changePct(double curr, double prev) {
        if (prev <= 0) return "ilk dönem verisi";
        double pct = (curr - prev) / prev * 100.0;
        return pct >= 0
                ? String.format("+%.1f%% artış", pct)
                : String.format("%.1f%% azalış", Math.abs(pct));
    }

    private String periodLabel(LocalDate start, LocalDate end) {
        DateTimeFormatter f = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("tr"));
        return start.format(f) + " – " + end.format(f);
    }

    private String nowLabel() {
        return LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", new Locale("tr"))) + " UTC";
    }

    private Long safeQueryLong(String sql, Object... args) {
        try { return jdbc.queryForObject(sql, Long.class, args); }
        catch (Exception e) { log.debug("safeQueryLong failed [{}]: {}", sql.substring(0, Math.min(50, sql.length())), e.getMessage()); return 0L; }
    }

    private Double safeQueryDouble(String sql, Object... args) {
        try { return jdbc.queryForObject(sql, Double.class, args); }
        catch (Exception e) { log.debug("safeQueryDouble failed: {}", e.getMessage()); return 0.0; }
    }

    private ReportContent errorReport(String title, LocalDate start, LocalDate end, String detail) {
        return new ReportContent(title, periodLabel(start, end), nowLabel(),
                List.of(new ReportSection("Hata",
                        List.of("Rapor üretilemedi. Lütfen daha sonra tekrar deneyin.",
                                detail != null ? "Teknik detay: " + detail : ""),
                        List.of())),
                "Rapor üretilemedi.");
    }
}
