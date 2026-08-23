package com.p2pwallet.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Simple bearer-token auth filter.
 * Reads token→userId mappings from the app.auth.tokens config property.
 * Format: "alice:tok_alice,bob:tok_bob"
 *
 * Skips auth for health/metrics/actuator endpoints.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthFilter.class);
    private static final Set<String> SKIP_PATHS = Set.of(
            "/actuator", "/actuator/health", "/actuator/prometheus",
            "/actuator/info", "/dashboard", "/metrics",
            "/swagger-ui", "/v3/api-docs"
    );

    private final Map<String, String> tokenToUser = new HashMap<>();

    public AuthFilter(@Value("${app.auth.tokens:}") String tokensConfig) {
        if (tokensConfig != null && !tokensConfig.isBlank()) {
            for (String pair : tokensConfig.split(",")) {
                String[] parts = pair.trim().split(":");
                if (parts.length == 2) {
                    tokenToUser.put(parts[1].trim(), parts[0].trim());
                }
            }
        }
        log.info("Auth filter initialized with {} token mappings", tokenToUser.size());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip auth for health/metrics endpoints
        if (SKIP_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid Authorization header\"}");
            return;
        }

        String token = authHeader.substring("Bearer ".length()).trim();
        String userId = tokenToUser.get(token);

        if (userId == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"status\":401,\"error\":\"UNAUTHORIZED\",\"message\":\"Invalid bearer token\"}");
            return;
        }

        // Set userId on request attribute and MDC for logging
        request.setAttribute("userId", userId);
        MDC.put("user_id", userId);

        filterChain.doFilter(request, response);
    }
}
