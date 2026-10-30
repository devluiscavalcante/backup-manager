package com.backup_manager.infrastructure.storage;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manifesto de um snapshot: caminho, tamanho, data de modificacao e SHA-256 de cada arquivo. Permite verificar
 * o backup antes de precisar dele (bit rot, falha de disco, arquivo alterado no destino). Fica na pasta de
 * metadados do proprio snapshot, fora do conteudo restauravel.
 */
public final class BackupManifest {

    public static final String FILE_NAME = "manifest.tsv";
    // Hash desligado por configuracao: o manifesto guarda so tamanho e data.
    static final String NO_HASH = "-";
    private static final String SEPARATOR = "\t";

    private BackupManifest() {
    }

    public static Path of(Path snapshotRoot) {
        return snapshotRoot.resolve(BackupMetadata.DIRECTORY).resolve(FILE_NAME);
    }

    public record Entry(String relativePath, long size, long modifiedMillis, String sha256) {
    }

    /**
     * Grava o manifesto do snapshot. Arquivo que e hardlink do mesmo arquivo em reference, com tamanho e data
     * iguais, herda o hash do manifesto de la sem ser relido: so o que foi copiado nesta execucao e lido de novo.
     */
    public static List<Entry> write(Path snapshotRoot, Path reference, boolean hashEnabled) throws IOException {
        Map<String, Entry> previous = read(of(reference));
        List<Entry> entries = new ArrayList<>();

        walkContent(snapshotRoot, (file, attrs) -> {
            String relative = relativePath(snapshotRoot, file);
            long modified = attrs.lastModifiedTime().toMillis();
            String hash = NO_HASH;
            if (hashEnabled) {
                Entry known = previous.get(relative);
                hash = known != null && !NO_HASH.equals(known.sha256()) && known.size() == attrs.size()
                        && known.modifiedMillis() == modified && isSameFile(file, reference.resolve(relative))
                        ? known.sha256()
                        : sha256(file);
            }
            entries.add(new Entry(relative, attrs.size(), modified, hash));
        });

        Path manifest = of(snapshotRoot);
        Files.createDirectories(manifest.getParent());
        try (BufferedWriter writer = Files.newBufferedWriter(manifest, StandardCharsets.UTF_8)) {
            for (Entry entry : entries) {
                // O caminho vai por ultimo: e o unico campo que pode conter qualquer caractere.
                writer.write(entry.sha256() + SEPARATOR + entry.size() + SEPARATOR + entry.modifiedMillis()
                        + SEPARATOR + entry.relativePath());
                writer.newLine();
            }
        }
        return entries;
    }

    /** Entradas do manifesto por caminho relativo; vazio se o snapshot nao tem manifesto. */
    public static Map<String, Entry> read(Path manifest) throws IOException {
        Map<String, Entry> entries = new HashMap<>();
        if (!Files.isRegularFile(manifest)) {
            return entries;
        }
        try (Stream<String> lines = Files.lines(manifest, StandardCharsets.UTF_8)) {
            lines.forEach(line -> {
                String[] fields = line.split(SEPARATOR, 4);
                if (fields.length == 4) {
                    try {
                        entries.put(fields[3], new Entry(fields[3], Long.parseLong(fields[1]),
                                Long.parseLong(fields[2]), fields[0]));
                    } catch (NumberFormatException ignored) {
                        // linha corrompida: o arquivo correspondente aparece como divergente na verificacao
                    }
                }
            });
        }
        return entries;
    }

    public record Verification(boolean manifestFound, int checked, List<String> missing, List<String> changed,
                               List<String> unexpected) {

        public boolean intact() {
            return manifestFound && missing.isEmpty() && changed.isEmpty() && unexpected.isEmpty();
        }
    }

    /**
     * Confere o snapshot contra o manifesto: arquivo ausente, com tamanho ou hash diferente e arquivo que nao
     * consta no manifesto. Le todos os arquivos com hash registrado, entao o custo e o de ler o backup inteiro.
     */
    public static Verification verify(Path snapshotRoot) throws IOException {
        Path manifest = of(snapshotRoot);
        if (!Files.isRegularFile(manifest)) {
            return new Verification(false, 0, List.of(), List.of(), List.of());
        }
        Map<String, Entry> expected = read(manifest);
        List<String> changed = new ArrayList<>();
        List<String> unexpected = new ArrayList<>();
        int[] checked = {0};

        walkContent(snapshotRoot, (file, attrs) -> {
            String relative = relativePath(snapshotRoot, file);
            Entry entry = expected.remove(relative);
            if (entry == null) {
                unexpected.add(relative);
                return;
            }
            checked[0]++;
            if (entry.size() != attrs.size()
                    || (!NO_HASH.equals(entry.sha256()) && !entry.sha256().equals(sha256OrNull(file)))) {
                changed.add(relative);
            }
        });

        List<String> missing = expected.keySet().stream().sorted().toList();
        return new Verification(true, checked[0], missing, changed.stream().sorted().toList(),
                unexpected.stream().sorted().toList());
    }

    // Arquivo ilegivel na verificacao conta como divergente em vez de interromper a conferencia do resto.
    private static String sha256OrNull(Path file) {
        try {
            return sha256(file);
        } catch (IOException e) {
            return null;
        }
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponivel na JVM", e);
        }
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private interface FileAction {
        void accept(Path file, BasicFileAttributes attrs) throws IOException;
    }

    // Conteudo do snapshot: arquivos regulares, sem a pasta de metadados (onde o proprio manifesto fica).
    private static void walkContent(Path snapshotRoot, FileAction action) throws IOException {
        Path metadata = snapshotRoot.resolve(BackupMetadata.DIRECTORY);
        Files.walkFileTree(snapshotRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                return dir.equals(metadata) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (attrs.isRegularFile()) {
                    action.accept(file, attrs);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static boolean isSameFile(Path file, Path other) {
        try {
            return Files.exists(other) && Files.isSameFile(file, other);
        } catch (IOException e) {
            return false;
        }
    }
}
