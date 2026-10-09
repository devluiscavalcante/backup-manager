package com.backup_manager.infrastructure.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Regras para nao seguir symlinks/junctions durante varreduras que partem de um caminho real.
 */
final class FileLinks {

    private FileLinks() {
    }

    // Em uma varredura iniciada por um caminho real, um diretorio cujo caminho real difere do visitado
    // e (ou passa por) um symlink/junction. Na duvida (erro ao resolver), trata como redirecionado.
    static boolean isRedirected(Path dir) {
        try {
            return !dir.toRealPath().equals(dir);
        } catch (IOException e) {
            return true;
        }
    }

    static boolean isLink(BasicFileAttributes attrs) {
        return attrs.isSymbolicLink() || attrs.isOther();
    }
}
