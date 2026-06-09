package com.backup_manager.domain.exception.handler;

import com.backup_manager.application.dto.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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

    @Test
    void unsupportedMethodShouldHandleNullMethod() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/example");

        HttpRequestMethodNotSupportedException exception = mock(HttpRequestMethodNotSupportedException.class);
        when(exception.getMethod()).thenReturn(null);
        when(exception.getSupportedMethods()).thenReturn(new String[]{"GET"});

        ResponseEntity<ApiErrorResponse> response =
                handler.handleHttpRequestMethodNotSupported(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("http_method_not_supported");
        assertThat(response.getBody().getPath()).isEqualTo("/api/example");
        assertThat(response.getBody().getDetails()).isInstanceOf(Map.class);

        Map<?, ?> details = (Map<?, ?>) response.getBody().getDetails();
        assertThat(details.containsKey("method")).isTrue();
        assertThat(details.get("method")).isNull();
        assertThat(details.get("supportedMethods")).isEqualTo(List.of("GET"));
    }

    @Test
    void unsupportedMediaTypeShouldHandleNullSupportedMediaTypes() {
        WebRequest request = mock(WebRequest.class);
        when(request.getDescription(false)).thenReturn("uri=/api/example");

        HttpMediaTypeNotSupportedException exception = mock(HttpMediaTypeNotSupportedException.class);
        when(exception.getContentType()).thenReturn(MediaType.APPLICATION_XML);
        when(exception.getSupportedMediaTypes()).thenReturn(null);

        ResponseEntity<ApiErrorResponse> response =
                handler.handleHttpMediaTypeNotSupported(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("http_media_type_not_supported");
        assertThat(response.getBody().getPath()).isEqualTo("/api/example");
        assertThat(response.getBody().getDetails()).isInstanceOf(Map.class);

        Map<?, ?> details = (Map<?, ?>) response.getBody().getDetails();
        assertThat(details.get("contentType")).isEqualTo("application/xml");
        assertThat(details.get("supportedContentTypes")).isEqualTo(List.of());
    }
}
