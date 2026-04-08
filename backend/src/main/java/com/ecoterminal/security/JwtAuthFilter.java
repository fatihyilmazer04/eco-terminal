package com.ecoterminal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Her HTTP isteğinde bir kez çalışan JWT doğrulama filtresi.
 *
 * Akış:
 *  1. "Authorization: Bearer <token>" header'ını al
 *  2. Token'ı parse et → email çıkar
 *  3. UserDetailsService ile kullanıcıyı yükle
 *  4. Token geçerliyse SecurityContext'e authentication set et
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Header yoksa veya "Bearer " ile başlamıyorsa devam et
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7); // "Bearer " sonrasını al

        try {
            final String userEmail = jwtService.extractUsername(jwt);

            // Henüz authenticate edilmemişse işle
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.validateAccessToken(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.debug("Authenticated user: {} | path: {}", userEmail, request.getRequestURI());
                }
            }
        } catch (Exception e) {
            log.warn("JWT filter error for path {}: {}", request.getRequestURI(), e.getMessage());
            // Exception'ı yutuyoruz — filter chain devam eder, Security 401 döndürür
        }

        filterChain.doFilter(request, response);
    }

    /** /api/auth/** yolları için filtreyi atla — token kontrolüne gerek yok */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.startsWith("/actuator/");
    }
}
