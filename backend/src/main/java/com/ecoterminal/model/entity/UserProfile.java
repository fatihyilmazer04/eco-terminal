package com.ecoterminal.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * Kullanıcının ek profil bilgileri.
 * User ile bire-bir ilişki — user_id üzerinden UNIQUE FK ile bağlı.
 * preferences_json: JSONB sütununa Map<String,Object> olarak persist edilir.
 */
@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "profile_id")
    private Long profileId;

    /**
     * CascadeType yok — User silinirse profile CASCADE ile DB tarafından silinir.
     * Uygulama katmanında ayrı cascade tanımlamıyoruz.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "full_name", length = 255)
    private String fullName;

    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * preferences_json → JSONB tipinde saklanır.
     * @JdbcTypeCode(SqlTypes.JSON) Hibernate'e PostgreSQL JSONB sütununa
     * doğru cast yapmasını söyler; columndefinition'sız String ile çalışır.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferences_json", columnDefinition = "jsonb")
    private String preferencesJson;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "fcm_token", length = 500)
    private String fcmToken;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
