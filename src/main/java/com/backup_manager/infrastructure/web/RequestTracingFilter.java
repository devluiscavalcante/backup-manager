package com.backup_manager.infrastructure.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestTracingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = resolveRequestId(request);

        request.setAttribute(RequestTracingContext.ATTRIBUTE_NAME, requestId);
        response.setHeader(RequestTracingContext.HEADER_NAME, requestId);
        MDC.put(RequestTracingContext.MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestTracingContext.MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String headerValue = request.getHeader(RequestTracingContext.HEADER_NAME);
        if (headerValue == null) {
            return UUID.randomUUID().toString();
        }

        String normalized = headerValue.trim();
        if (normalized.isEmpty() || normalized.length() > 128) {
            return UUID.randomUUID().toString();
        }

        return normalized;
    }
}
