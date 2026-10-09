package com.backup_manager.application.service;

import com.backup_manager.infrastructure.config.AppSecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
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
            throw new IllegalArgumentException(
                    "O caminho informado nao pode estar vazio para a operacao de " + operationName + "."
            );
        }

        Path normalizedPath = Paths.get(rawPath).toAbsolutePath().normalize();
        ensureNotPathTraversal(rawPath, operationName);

        // Valida o caminho efetivamente acessado: symlinks/junctions nao podem levar para fora da allowlist.
        Path realPath = resolveRealPath(normalizedPath, operationName);
        ensureNotProtectedSystemPath(realPath, operationName);
        ensureWithinAllowedRoots(realPath, operationName);
        return realPath;
    }

    public Path validateWritableManagedPath(String rawPath, String operationName) {
        Path normalizedPath = validateManagedPath(rawPath, operationName);
        Path parent = normalizedPath.getParent();

        if (parent != null && Files.exists(parent) && !Files.isWritable(parent)) {
            throw new SecurityException(
                    "Sem permissao de escrita no destino informado para a operacao de " + operationName + "."
            );
        }

        return normalizedPath;
    }

    public void ensureNotOverlapping(Path sourcePath, Path targetPath, String operationName) {
        Path source = resolveRealPath(sourcePath.toAbsolutePath().normalize(), operationName);
        Path target = resolveRealPath(targetPath.toAbsolutePath().normalize(), operationName);

        if (target.startsWith(source) || source.startsWith(target)) {
            logger.warn("Sobreposicao de caminhos bloqueada em {}: {} <-> {}", operationName, source, target);
            throw new IllegalArgumentException(
                    "Origem e destino nao podem estar um dentro do outro na operacao de " + operationName + "."
            );
        }
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
            throw new SecurityException(
                    "O caminho informado para a operacao de " + operationName
                            + " pertence a uma area protegida do sistema."
            );
        }
    }

    private void ensureWithinAllowedRoots(Path normalizedPath, String operationName) {
        List<Path> allowedRoots = getAllowedRoots();
        boolean isAllowed = allowedRoots.stream().anyMatch(normalizedPath::startsWith);

        if (!isAllowed) {
            logger.warn("Caminho fora da allowlist bloqueado em {}: {}", operationName, normalizedPath);
            throw new SecurityException(
                    "O caminho informado para a operacao de " + operationName
                            + " nao pertence a uma raiz permitida."
            );
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
                .map(path -> realPathOrSelf(Paths.get(path).toAbsolutePath().normalize()))
                .toList();
    }

    // Resolve o ancestral existente mais proximo para o caminho real e reaplica o trecho ainda inexistente
    // (destinos de backup/restauracao podem ainda nao existir).
    private Path resolveRealPath(Path normalizedPath, String operationName) {
        Path existingAncestor = normalizedPath;
        while (existingAncestor != null && !Files.exists(existingAncestor, LinkOption.NOFOLLOW_LINKS)) {
            existingAncestor = existingAncestor.getParent();
        }

        if (existingAncestor == null) {
            return normalizedPath;
        }

        try {
            Path remainder = existingAncestor.relativize(normalizedPath);
            return existingAncestor.toRealPath().resolve(remainder).normalize();
        } catch (IOException e) {
            logger.warn("Caminho real nao resolvido em {}: {} ({})", operationName, normalizedPath, e.getMessage());
            throw new SecurityException(
                    "Nao foi possivel resolver o caminho real informado para a operacao de " + operationName + "."
            );
        }
    }

    private Path realPathOrSelf(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }
}
