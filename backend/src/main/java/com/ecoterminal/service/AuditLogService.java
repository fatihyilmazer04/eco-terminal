package com.ecoterminal.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Merkezi audit log servisi.
 * Tüm admin işlemleri ve sistem olayları bu servis üzerinden audit_logs tablosuna yazılır.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final JdbcTemplate jdbcTemplate;

    private static final String INSERT_SQL =
            "INSERT INTO audit_logs (actor_id, action_type, target_table, target_id, old_value, new_value) " +
            "VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb)";

    /**
     * Kullanıcı tarafından tetiklenen admin işlemini logla.
     *
     * @param actorId     İşlemi yapan admin'in user ID'si
     * @param actionType  Eylem tipi (örn. REDIRECT, ENERGY_SETTING, USER_UPDATE)
     * @param targetTable Etkilenen tablo adı
     * @param targetId    Etkilenen kaydın ID'si
     * @param oldValue    JSON formatında eski değer (null kabul edilir)
     * @param newValue    JSON formatında yeni değer (null kabul edilir)
     */
    public void log(Long actorId, String actionType, String targetTable, Long targetId,
                    String oldValue, String newValue) {
        try {
            jdbcTemplate.update(INSERT_SQL, actorId, actionType, targetTable, targetId, oldValue, newValue);
        } catch (Exception e) {
            // Audit log hatası ana işlemi durdurmamalı
            log.error("Audit log yazılamadı: action={}, target={}/{}, hata={}",
                    actionType, targetTable, targetId, e.getMessage());
        }
    }

    /**
     * Sistem tarafından tetiklenen olayları logla (scheduler, otomatik temizleme vb.).
     * actor_id = null olarak kaydedilir.
     *
     * @param actionType  Eylem tipi (örn. DATA_RETENTION, SYSTEM_CLEANUP)
     * @param targetTable Etkilenen tablo adı
     * @param details     JSON formatında detay bilgisi
     */
    public void logSystem(String actionType, String targetTable, String details) {
        try {
            jdbcTemplate.update(INSERT_SQL, null, actionType, targetTable, null, null, details);
        } catch (Exception e) {
            log.error("Sistem audit log yazılamadı: action={}, hata={}", actionType, e.getMessage());
        }
    }
}
