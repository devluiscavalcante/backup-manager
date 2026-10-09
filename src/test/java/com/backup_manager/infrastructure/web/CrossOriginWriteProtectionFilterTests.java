package com.backup_manager.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CrossOriginWriteProtectionFilterTests {

    private final CrossOriginWriteProtectionFilter filter = new CrossOriginWriteProtectionFilter(
            List.of(" http://localhost:4200/ "), new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void shouldAllowSafeMethodFromForeignOrigin() throws Exception {
        MockHttpServletRequest request = request("GET");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldBlockWriteFromForeignOrigin() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader(HttpHeaders.ORIGIN, "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("cross_origin_request_blocked");
    }

    @Test
    void shouldAllowWriteFromConfiguredOriginIgnoringCaseAndTrailingSlash() throws Exception {
        MockHttpServletRequest request = request("DELETE");
        request.addHeader(HttpHeaders.ORIGIN, "HTTP://LOCALHOST:4200");

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldAllowWriteFromSameOrigin() throws Exception {
        MockHttpServletRequest request = request("PUT");
        request.setServerPort(8080);
        request.addHeader(HttpHeaders.ORIGIN, "http://localhost:8080");

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldAllowWriteWithoutOriginFromNonBrowserClient() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request("POST"), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void shouldBlockWriteWithoutOriginWhenBrowserDeclaresCrossSite() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.addHeader("Sec-Fetch-Site", "cross-site");
        MockHttpServletResponse response = new MockHttpServletResponse();

        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/backup");
        request.setScheme("http");
        request.setServerName("localhost");
        return request;
    }
}
