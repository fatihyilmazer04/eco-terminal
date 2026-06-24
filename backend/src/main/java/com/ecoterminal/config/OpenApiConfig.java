package com.ecoterminal.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger / OpenAPI konfigürasyonu.
 *
 * Erişim kısıtlaması: Yalnızca ADMIN rolüne sahip kullanıcılar
 * Swagger UI ve /api-docs endpoint'lerine ulaşabilir.
 * (SecurityConfig.java içinde hasRole("ADMIN") kuralıyla sağlanmaktadır.)
 *
 * Kullanım:
 *   1. POST /api/auth/login ile admin token alın.
 *   2. Swagger UI'da "Authorize" butonuna tıklayın.
 *   3. Token değerini "Bearer <token>" formatında girin.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(apiInfo())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME_NAME, bearerSecurityScheme()));
    }

    private Info apiInfo() {
        return new Info()
                .title("Eco-Terminal API")
                .description("""
                        Akıllı Havalimanı Yoğunluk ve Enerji Yönetim Sistemi — Admin API Dokümantasyonu.

                        **Erişim:** Bu sayfa yalnızca ADMIN rolüne açıktır.

                        **Kimlik doğrulama adımları:**
                        1. `POST /api/auth/login` ile giriş yapın.
                        2. Dönen `accessToken` değerini kopyalayın.
                        3. Sağ üstteki **Authorize** butonuna tıklayın.
                        4. `Bearer <accessToken>` formatında girin.
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("Eco-Terminal Ekibi")
                        .email("admin@ecoterminal.com"));
    }

    private SecurityScheme bearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT access token. Login sonrası alınan 'accessToken' değerini girin.");
    }
}
