package com.backup_manager.application.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PathSecurityServiceTests {

    @TempDir
    Path tempDir;

    @Test
    void shouldAllowPathInsideConfiguredRoot() {
        Object service = createService(List.of(tempDir.toString()));
        Path allowedFile = tempDir.resolve("documents").resolve("backup.zip");

        assertDoesNotThrow(() -> validateManagedPath(service, allowedFile.toString(), "backup"));
    }

    @Test
    void shouldBlockPathOutsideConfiguredRoot() {
        Object service = createService(List.of(tempDir.toString()));
        Path outsidePath = tempDir.getParent().resolve("fora-da-allowlist");

        SecurityException exception = assertThrows(SecurityException.class,
                () -> validateManagedPath(service, outsidePath.toString(), "backup"));

        assertEquals(
                "O caminho informado para a operacao de backup nao pertence a uma raiz permitida.",
                exception.getMessage()
        );
    }

    @Test
    void shouldBlockPathTraversalAttempt() {
        Object service = createService(List.of(tempDir.toString()));
        String traversalPath = tempDir.resolve("docs").resolve("..").resolve("segredo").toString();

        SecurityException exception = assertThrows(SecurityException.class,
                () -> validateManagedPath(service, traversalPath, "backup"));

        assertEquals("Path traversal detectado na operacao de backup.", exception.getMessage());
    }

    @Test
    void shouldRejectBlankManagedPath() {
        Object service = createService(List.of(tempDir.toString()));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> validateManagedPath(service, "   ", "backup"));

        assertEquals(
                "O caminho informado nao pode estar vazio para a operacao de backup.",
                exception.getMessage()
        );
    }

    @Test
    void shouldFallbackToUserHomeWhenAllowlistIsEmpty() {
        Object service = createService(List.of());
        Path homePath = Paths.get(System.getProperty("user.home")).resolve("backup-test");

        assertDoesNotThrow(() -> validateManagedPath(service, homePath.toString(), "backup"));
    }

    private Object createService(List<String> allowedRoots) {
        try {
            Class<?> propertiesClass = Class.forName("com.backup_manager.infrastructure.config.AppSecurityProperties");
            Object properties = propertiesClass.getDeclaredConstructor().newInstance();
            Method setAllowedPathRoots = propertiesClass.getMethod("setAllowedPathRoots", List.class);
            setAllowedPathRoots.invoke(properties, allowedRoots);

            Class<?> serviceClass = Class.forName("com.backup_manager.application.service.PathSecurityService");
            return serviceClass.getDeclaredConstructor(propertiesClass).newInstance(properties);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel instanciar PathSecurityService para o teste.", e);
        }
    }

    private void validateManagedPath(Object service, String path, String operationName) {
        try {
            Method validateManagedPath = service.getClass().getMethod("validateManagedPath", String.class, String.class);
            validateManagedPath.invoke(service, path, operationName);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Falha inesperada ao executar validacao de caminho.", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel executar validateManagedPath no teste.", e);
        }
    }
}
