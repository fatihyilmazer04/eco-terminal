package com.ecoterminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demo/geliştirme ortamında uçuş saatlerini uygulama başlangıcında tazeleyerek
 * seed uçuşlarının her zaman "gelecekte" kalmasını sağlar.
 *
 * Sadece seed'den gelen flight_id 1–5 hedeflenir; kullanıcı tarafından eklenen
 * uçuşlara dokunulmaz.
 *
 * Üretim ortamında application.yml'de  demo.refresh-flights: false  yapılarak
 * tamamen devre dışı bırakılabilir.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataRefresher {

    private final JdbcTemplate jdbcTemplate;

    @Value("${demo.refresh-flights:true}")
    private boolean refreshFlights;

    /**
     * ApplicationReadyEvent: Flyway migration'ları ve tüm bean'ler hazırlandıktan
     * sonra, HTTP istekleri kabul edilmeden hemen önce tetiklenir.
     * Bu sayede Flyway'in yeni migration'ları uygulamasının ardından çalışır.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void refreshDemoFlights() {
        if (!refreshFlights) {
            log.info("Demo uçuş tazeleme devre dışı (demo.refresh-flights=false)");
            return;
        }

        log.info("Demo uçuş saatleri tazeleniyor...");

        // Her seed uçuşu için kalkış/varış saatini NOW() baz alarak güncelle.
        // Dağılım: passenger'ın uçuşları 2 ve 4 saat sonra, diğerleri kademeli.
        // arrival_time = departure_time + uçuş süresi (sabit)
        int updated = 0;

        updated += jdbcTemplate.update(
            "UPDATE flights SET departure_time = NOW() + INTERVAL '2 hours', " +
            "                   arrival_time   = NOW() + INTERVAL '5 hours'   " +
            "WHERE flight_id = 1"   // TK1234 — passenger'ın bileti (en yakın)
        );

        updated += jdbcTemplate.update(
            "UPDATE flights SET departure_time = NOW() + INTERVAL '4 hours', " +
            "                   arrival_time   = NOW() + INTERVAL '6 hours 30 minutes' " +
            "WHERE flight_id = 2"   // PC5678 — passenger'ın ikinci bileti
        );

        updated += jdbcTemplate.update(
            "UPDATE flights SET departure_time = NOW() + INTERVAL '6 hours', " +
            "                   arrival_time   = NOW() + INTERVAL '7 hours 30 minutes' " +
            "WHERE flight_id = 3"   // XQ9101 — alice'in bileti
        );

        updated += jdbcTemplate.update(
            "UPDATE flights SET departure_time = NOW() + INTERVAL '3 hours', " +
            "                   arrival_time   = NOW() + INTERVAL '14 hours'  " +
            "WHERE flight_id = 4"   // TK2468 — bob'un uzun uçuşu
        );

        updated += jdbcTemplate.update(
            "UPDATE flights SET departure_time = NOW() + INTERVAL '5 hours', " +
            "                   arrival_time   = NOW() + INTERVAL '7 hours 30 minutes' " +
            "WHERE flight_id = 5"   // PC1357 — yusuf'un bileti
        );

        log.info("Demo uçuş saatleri tazelendi: {} uçuş güncellendi", updated);
    }
}
