package com.backup_manager.infrastructure.web;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Bloqueia requisicoes de escrita disparadas por paginas de outra origem.
 * Navegadores sempre enviam Origin em POST/PUT/PATCH/DELETE cross-origin; clientes fora do navegador
 * (curl, scripts) nao enviam e continuam permitidos.
 */
public class CrossOriginWriteProtectionFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(CrossOriginWriteProtectionFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";

    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public CrossOriginWriteProtectionFilter(List<String> allowedOrigins, ObjectMapper objectMapper) {
        this.allowedOrigins = allowedOrigins == null ? Set.of() : allowedOrigins.stream()
                .filter(Objects::nonNull)
                .map(CrossOriginWriteProtectionFilter::normalizeOrigin)
                .filter(origin -> !origin.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase(Locale.ROOT)) || isTrustedOrigin(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        logger.warn("Escrita cross-origin bloqueada: method={}, path={}, origin={}, secFetchSite={}",
                request.getMethod(), request.getRequestURI(),
                request.getHeader(HttpHeaders.ORIGIN), request.getHeader(SEC_FETCH_SITE));

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(
                        HttpStatus.FORBIDDEN,
                        "Requisicao de escrita de origem nao permitida.",
                        "cross_origin_request_blocked",
                        Map.of("method", request.getMethod()),
                        request.getRequestURI()
                )
        );
    }

    private boolean isTrustedOrigin(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (origin == null) {
            // Sem Origin: cliente fora do navegador. Se o navegador declarar cross-site, bloqueia mesmo assim.
            return !"cross-site".equalsIgnoreCase(request.getHeader(SEC_FETCH_SITE));
        }

        String normalizedOrigin = normalizeOrigin(origin);
        return normalizedOrigin.equals(requestOrigin(request)) || allowedOrigins.contains(normalizedOrigin);
    }

    private static String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme().toLowerCase(Locale.ROOT);
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String host = request.getServerName().toLowerCase(Locale.ROOT);
        return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
    }

    private static String normalizeOrigin(String origin) {
        String normalized = origin.trim().toLowerCase(Locale.ROOT);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
