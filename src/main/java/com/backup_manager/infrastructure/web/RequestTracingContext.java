package com.backup_manager.infrastructure.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

public final class RequestTracingContext {

    public static final String HEADER_NAME = "X-Request-Id";
    public static final String ATTRIBUTE_NAME = RequestTracingContext.class.getName() + ".requestId";
    public static final String MDC_KEY = "requestId";

    private RequestTracingContext() {
    }

    public static String currentRequestId() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes servletRequestAttributes) {
            HttpServletRequest request = servletRequestAttributes.getRequest();
            Object requestId = request.getAttribute(ATTRIBUTE_NAME);
            if (requestId instanceof String value && !value.isBlank()) {
                return value;
            }
        }

        String requestId = MDC.get(MDC_KEY);
        return requestId == null || requestId.isBlank() ? null : requestId;
    }
}
