package com.backup_manager.application.service;

import com.backup_manager.infrastructure.config.AppSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

@Service
public class PathSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(PathSecurityService.class);

    private final AppSecurityProperties securityProperties;

    public PathSecurityService(AppSecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    public Path validateManagedPath(String rawPath, String operationName) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Caminho nao pode estar vazio para " + operationName);
        }

        Path normalizedPath = Paths.get(rawPath).toAbsolutePath().normalize();
        ensureNotPathTraversal(rawPath, operationName);
        ensureNotProtectedSystemPath(normalizedPath, operationName);
        ensureWithinAllowedRoots(normalizedPath, operationName);
        return normalizedPath;
    }

    public Path validateWritableManagedPath(String rawPath, String operationName) {
        Path normalizedPath = validateManagedPath(rawPath, operationName);
        Path parent = normalizedPath.getParent();

        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            throw new SecurityException("Sem permissao de escrita no destino informado para " + operationName + ".");
        }

        return normalizedPath;
    }

    private void ensureNotPathTraversal(String rawPath, String operationName) {
        if (rawPath.contains("..")) {
            logger.warn("Path traversal bloqueado em {}: {}", operationName, rawPath);
            throw new SecurityException("Path traversal detectado na operacao de " + operationName + ".");
        }
    }

    private void ensureNotProtectedSystemPath(Path normalizedPath, String operationName) {
        String normalized = normalizedPath.toString().toLowerCase();
        String rootDir = System.getenv("SystemRoot");
        String windowsDir = rootDir != null ? rootDir.toLowerCase() : "c:\\windows";

        boolean isForbidden = normalized.startsWith(windowsDir)
                || normalized.contains("system32")
                || normalized.contains("syswow64")
                || normalized.contains("program files")
                || normalized.matches("^[a-z]:\\\\$");

        if (isForbidden) {
            logger.warn("Caminho protegido bloqueado em {}: {}", operationName, normalizedPath);
            throw new SecurityException("O caminho informado pertence a uma area protegida do sistema.");
        }
    }

    private void ensureWithinAllowedRoots(Path normalizedPath, String operationName) {
        List<Path> allowedRoots = getAllowedRoots();
        boolean isAllowed = allowedRoots.stream().anyMatch(normalizedPath::startsWith);

        if (!isAllowed) {
            logger.warn("Caminho fora da allowlist bloqueado em {}: {}", operationName, normalizedPath);
            throw new SecurityException("O caminho informado nao pertence a uma raiz permitida.");
        }
    }

    private List<Path> getAllowedRoots() {
        if (securityProperties.getAllowedPathRoots() == null || securityProperties.getAllowedPathRoots().isEmpty()) {
            return List.of(Paths.get(System.getProperty("user.home")).toAbsolutePath().normalize());
        }

        return securityProperties.getAllowedPathRoots().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(path -> !path.isEmpty())
                .map(path -> Paths.get(path).toAbsolutePath().normalize())
                .toList();
    }
}
