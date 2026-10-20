package com.backup_manager.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FailedLoginThrottleFilterTests {

    private static final String BASIC = "Basic YWRtaW46ZXJyYWRh";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final FailedLoginThrottleFilter filter =
            new FailedLoginThrottleFilter(3, Duration.ofMinutes(5), new ObjectMapper().findAndRegisterModules(), clock);
    private final AtomicInteger chainCalls = new AtomicInteger();

    @Test
    void shouldLockClientAfterMaxRejectedCredentials() throws Exception {
        for (int i = 0; i < 3; i++) {
            assertThat(perform("10.0.0.1", BASIC, 401).getStatus()).isEqualTo(401);
        }

        MockHttpServletResponse blocked = perform("10.0.0.1", BASIC, 200);

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("300");
        assertThat(blocked.getContentAsString()).contains("too_many_failed_logins");
        // Bloqueado antes da autenticacao: nem a senha correta chega ao BasicAuthenticationFilter.
        assertThat(chainCalls.get()).isEqualTo(3);
    }

    @Test
    void shouldOnlyLockTheOffendingClient() throws Exception {
        for (int i = 0; i < 3; i++) {
            perform("10.0.0.1", BASIC, 401);
        }

        assertThat(perform("10.0.0.2", BASIC, 200).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldResetCounterAfterSuccessfulAuthentication() throws Exception {
        perform("10.0.0.1", BASIC, 401);
        perform("10.0.0.1", BASIC, 401);
        perform("10.0.0.1", BASIC, 200);
        perform("10.0.0.1", BASIC, 401);
        perform("10.0.0.1", BASIC, 401);

        assertThat(perform("10.0.0.1", BASIC, 200).getStatus()).isEqualTo(200);
    }

    @Test
    void errorResponsesShouldNotResetTheCounter() throws Exception {
        perform("10.0.0.1", BASIC, 401);
        perform("10.0.0.1", BASIC, 401);
        perform("10.0.0.1", BASIC, 403);
        perform("10.0.0.1", BASIC, 401);

        assertThat(perform("10.0.0.1", BASIC, 200).getStatus()).isEqualTo(429);
    }

    @Test
    void shouldReleaseClientWhenWindowExpires() throws Exception {
        for (int i = 0; i < 3; i++) {
            perform("10.0.0.1", BASIC, 401);
        }

        clock.advance(Duration.ofMinutes(5));

        assertThat(perform("10.0.0.1", BASIC, 200).getStatus()).isEqualTo(200);
    }

    @Test
    void shouldIgnoreRequestsWithoutBasicCredentials() throws Exception {
        for (int i = 0; i < 5; i++) {
            perform("10.0.0.1", null, 401);
        }

        assertThat(perform("10.0.0.1", BASIC, 200).getStatus()).isEqualTo(200);
    }

    private MockHttpServletResponse perform(String remoteAddr, String authorization, int chainStatus)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/backup/active");
        request.setRemoteAddr(remoteAddr);
        if (authorization != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> {
            chainCalls.incrementAndGet();
            ((MockHttpServletResponse) res).setStatus(chainStatus);
        };

        filter.doFilter(request, response, chain);
        return response;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
