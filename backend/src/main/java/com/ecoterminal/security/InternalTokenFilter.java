package com.ecoterminal.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Checks incoming requests for the X-Internal-Token header.
 *
 * If the header is present and matches the configured token, the request is
 * authenticated as an internal service (ROLE_INTERNAL_SERVICE + ROLE_USER),
 * bypassing JWT. This allows llm-service to call backend endpoints without a
 * user JWT.
 *
 * This filter runs BEFORE JwtAuthFilter so that internal requests are already
 * authenticated when the JWT filter runs (it will skip already-authenticated contexts).
 */
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-Internal-Token";
    private static final String PRINCIPAL_NAME = "llm-service";

    @Value("${app.internal.token:}")
    private String configuredToken;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String incomingToken = request.getHeader(HEADER_NAME);

        if (StringUtils.hasText(incomingToken)
                && StringUtils.hasText(configuredToken)
                && configuredToken.equals(incomingToken)) {

            // Token matched — authenticate as internal service
            var authorities = List.of(
                    new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"),
                    new SimpleGrantedAuthority("ROLE_USER")
            );
            var auth = new UsernamePasswordAuthenticationToken(PRINCIPAL_NAME, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(auth);
            log.debug("internal_token_auth_ok remote={}", request.getRemoteAddr());

        } else if (StringUtils.hasText(incomingToken)) {
            // Header present but token wrong — reject immediately
            log.warn("internal_token_rejected remote={} uri={}", request.getRemoteAddr(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Invalid internal token\"}");
            return;
        }
        // No header → pass through; JwtAuthFilter will handle normal users

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Skip for auth and actuator endpoints — they are already public
        String path = request.getRequestURI();
        return path.startsWith("/api/auth/") || path.startsWith("/actuator/");
    }
}
