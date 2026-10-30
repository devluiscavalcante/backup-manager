package com.backup_manager.infrastructure.storage;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Snapshots por execucao dentro da pasta de metadados do destino. Cada execucao grava uma arvore completa em
 * snapshots/&lt;data&gt;-&lt;id&gt;; o que nao mudou desde o snapshot anterior vira hardlink dele, entao uma execucao
 * nunca sobrescreve o que outra gravou. Enquanto a copia nao termina, a pasta leva o sufixo .partial.
 */
public final class BackupSnapshots {

    public static final String DIRECTORY = "snapshots";
    static final String PARTIAL_SUFFIX = ".partial";

    private static final DateTimeFormatter NAME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    // O nome comeca pela data: a ordem alfabetica e a cronologica. So pastas nesse formato sao gerenciadas.
    private static final Pattern NAME_PATTERN = Pattern.compile("\\d{8}-\\d{6}-\\d+");

    private BackupSnapshots() {
    }

    public static Path directory(Path destinationRoot) {
        return destinationRoot.resolve(BackupMetadata.DIRECTORY).resolve(DIRECTORY);
    }

    /** Pasta definitiva do snapshot de uma execucao (deterministica: a retomada reencontra a mesma). */
    public static Path snapshotFor(Path destinationRoot, long taskId, LocalDateTime startedAt) {
        return directory(destinationRoot).resolve(NAME_FORMAT.format(startedAt) + "-" + taskId);
    }

    public static Path partialOf(Path snapshot) {
        return snapshot.resolveSibling(snapshot.getFileName() + PARTIAL_SUFFIX);
    }

    /** Inicio da execucao que gravou o snapshot, lido do nome da pasta. */
    public static LocalDateTime startedAt(Path snapshot) {
        return LocalDateTime.parse(snapshot.getFileName().toString().substring(0, 15), NAME_FORMAT);
    }

    /** Snapshots concluidos, do mais recente para o mais antigo. */
    public static List<Path> completed(Path destinationRoot) throws IOException {
        Path directory = directory(destinationRoot);
        if (!Files.isDirectory(directory)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(Files::isDirectory)
                    .filter(path -> NAME_PATTERN.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
    }

    /**
     * Base para os hardlinks da proxima execucao: o ultimo snapshot concluido ou, num destino do formato
     * antigo (espelho na raiz), a propria raiz. Arquivos ausentes na base simplesmente sao copiados.
     */
    public static Path referenceFor(Path destinationRoot) throws IOException {
        List<Path> snapshots = completed(destinationRoot);
        return snapshots.isEmpty() ? destinationRoot : snapshots.get(0);
    }

    /**
     * Remove snapshots .partial deixados por execucoes que falharam, foram canceladas ou interrompidas por um
     * restart. So pode ser chamado quando nenhuma outra tarefa esta ativa no destino.
     */
    public static void deleteStalePartials(Path destinationRoot, Path keep) throws IOException {
        Path directory = directory(destinationRoot);
        if (!Files.isDirectory(directory)) {
            return;
        }
        List<Path> stale;
        try (Stream<Path> entries = Files.list(directory)) {
            stale = entries
                    .filter(path -> path.getFileName().toString().endsWith(PARTIAL_SUFFIX))
                    .filter(path -> !path.equals(keep))
                    .toList();
        }
        for (Path partial : stale) {
            deleteTree(partial);
        }
    }

    /** Publica o snapshot: a pasta .partial vira a definitiva num unico rename. */
    public static void publish(Path partial, Path snapshot) throws IOException {
        try {
            Files.move(partial, snapshot, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(partial, snapshot);
        }
    }

    /**
     * Apaga uma arvore sem seguir links. Apagar um hardlink so remove aquela entrada: o conteudo continua
     * nos outros snapshots que apontam para ele.
     */
    public static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (exc != null) {
                    throw exc;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
