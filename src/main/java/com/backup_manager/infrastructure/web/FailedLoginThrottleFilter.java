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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limita tentativas de senha por IP: o HTTP Basic aceita um palpite por requisicao, sem nenhum custo
 * para quem tenta adivinhar. Depois de N credenciais recusadas dentro da janela, o IP fica bloqueado
 * (429) ate a janela expirar; uma autenticacao aceita zera o contador.
 */
public class FailedLoginThrottleFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(FailedLoginThrottleFilter.class);
    private static final int MAX_TRACKED_CLIENTS = 10_000;

    private final int maxFailures;
    private final Duration lockout;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Attempts> attemptsByClient = new ConcurrentHashMap<>();

    public FailedLoginThrottleFilter(int maxFailures, Duration lockout, ObjectMapper objectMapper, Clock clock) {
        this.maxFailures = maxFailures;
        this.lockout = lockout;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // Qualquer Authorization conta como tentativa: o BasicAuthenticationConverter aceita variantes
        // (trim, caixa, separador apos "Basic") que um teste textual do prefixo deixaria passar sem limite.
        if (request.getHeader(HttpHeaders.AUTHORIZATION) == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String client = request.getRemoteAddr();
        Instant now = clock.instant();
        Attempts current = attemptsByClient.get(client);
        if (current != null && current.isLocked(now, maxFailures)) {
            reject(request, response, Duration.between(now, current.windowEnd()));
            return;
        }

        filterChain.doFilter(request, response);

        int status = response.getStatus();
        if (status == HttpStatus.UNAUTHORIZED.value()) {
            recordFailure(client, now);
        } else if (status < 400) {
            // So uma resposta de sucesso prova que a credencial foi aceita; erros nao zeram o contador.
            attemptsByClient.remove(client);
        }
    }

    private void recordFailure(String client, Instant now) {
        if (attemptsByClient.size() >= MAX_TRACKED_CLIENTS) {
            attemptsByClient.values().removeIf(attempts -> !now.isBefore(attempts.windowEnd()));
        }

        Attempts updated = attemptsByClient.compute(client, (key, attempts) ->
                attempts == null || !now.isBefore(attempts.windowEnd())
                        ? new Attempts(1, now.plus(lockout))
                        : new Attempts(attempts.failures() + 1, attempts.windowEnd()));

        if (updated.failures() == maxFailures) {
            logger.warn("Login bloqueado temporariamente para {} apos {} credenciais recusadas", client, maxFailures);
        }
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, Duration retryAfter)
            throws IOException {
        long seconds = Math.max(1, retryAfter.toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(seconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiErrorResponse.of(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "Muitas tentativas de login recusadas. Tente novamente mais tarde.",
                        "too_many_failed_logins",
                        Map.of("retryAfterSeconds", seconds),
                        request.getRequestURI()
                )
        );
    }

    private record Attempts(int failures, Instant windowEnd) {
        boolean isLocked(Instant now, int maxFailures) {
            return failures >= maxFailures && now.isBefore(windowEnd);
        }
    }
}
