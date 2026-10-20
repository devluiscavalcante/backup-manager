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
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PathSecurityService {

    private static final Logger logger = LoggerFactory.getLogger(PathSecurityService.class);
    private static final Pattern PATH_SEPARATORS = Pattern.compile("[/\\\\]");

    // Segmentos protegidos em qualquer unidade (ex.: D:\Program Files), comparados por nome inteiro.
    private static final Set<String> PROTECTED_WINDOWS_SEGMENTS = Set.of(
            "system32", "syswow64", "program files", "program files (x86)"
    );

    // Areas do sistema em Linux (imagem Docker): o conteudo e do SO, nunca dado de usuario.
    private static final List<String> PROTECTED_UNIX_ROOTS = List.of(
            "/bin", "/boot", "/dev", "/etc", "/lib", "/lib64", "/proc", "/sbin", "/sys", "/usr"
    );

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

    // So o segmento ".." navega para cima; nomes como "v1..v2" ou "arquivo..bak" sao legitimos.
    private void ensureNotPathTraversal(String rawPath, String operationName) {
        boolean hasParentSegment = PATH_SEPARATORS.splitAsStream(rawPath).anyMatch(".."::equals);
        if (hasParentSegment) {
            logger.warn("Path traversal bloqueado em {}: {}", operationName, rawPath);
            throw new SecurityException("Path traversal detectado na operacao de " + operationName + ".");
        }
    }

    private void ensureNotProtectedSystemPath(Path normalizedPath, String operationName) {
        if (isProtectedSystemPath(normalizedPath)) {
            logger.warn("Caminho protegido bloqueado em {}: {}", operationName, normalizedPath);
            throw new SecurityException(
                    "O caminho informado para a operacao de " + operationName
                            + " pertence a uma area protegida do sistema."
            );
        }
    }

    static boolean isProtectedSystemPath(Path path) {
        // Raiz de unidade/sistema de arquivos (C:\ ou /): backup ou restauracao do disco inteiro.
        if (path.getParent() == null) {
            return true;
        }

        String rootDir = System.getenv("SystemRoot");
        Path windowsDir = Paths.get(rootDir != null ? rootDir : "C:\\Windows");
        if (startsWithIgnoreCase(path, windowsDir)) {
            return true;
        }

        for (Path segment : path) {
            if (PROTECTED_WINDOWS_SEGMENTS.contains(segment.toString().toLowerCase(Locale.ROOT))) {
                return true;
            }
        }

        String unixPath = path.toString().replace('\\', '/');
        return PROTECTED_UNIX_ROOTS.stream()
                .anyMatch(root -> unixPath.equals(root) || unixPath.startsWith(root + "/"));
    }

    // Prefixo por segmento e sem diferenciar maiusculas (NTFS): "C:\WINDOWS\x" esta em "C:\Windows".
    private static boolean startsWithIgnoreCase(Path path, Path prefix) {
        if (path.getNameCount() < prefix.getNameCount()
                || !String.valueOf(path.getRoot()).equalsIgnoreCase(String.valueOf(prefix.getRoot()))) {
            return false;
        }
        for (int i = 0; i < prefix.getNameCount(); i++) {
            if (!path.getName(i).toString().equalsIgnoreCase(prefix.getName(i).toString())) {
                return false;
            }
        }
        return true;
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
