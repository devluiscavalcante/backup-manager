package com.backup_manager.domain.exception.handler;

import com.backup_manager.application.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.context.request.WebRequest;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTests {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unsupportedMethodShouldHandleNullSupportedMethods() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/example");

        HttpRequestMethodNotSupportedException exception =
                new HttpRequestMethodNotSupportedException("PATCH", (Collection<String>) null);

        ResponseEntity<ApiErrorResponse> response =
                handler.handleHttpRequestMethodNotSupported(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("http_method_not_supported");
        assertThat(response.getBody().getPath()).isEqualTo("/api/example");
        assertThat(response.getBody().getDetails()).isInstanceOf(Map.class);

        Map<?, ?> details = (Map<?, ?>) response.getBody().getDetails();
        assertThat(details.get("method")).isEqualTo("PATCH");
        assertThat(details.get("supportedMethods")).isEqualTo(List.of());
    }
}
