package com.backup_manager.domain.exception;

public class FolderNotFoundException extends RuntimeException{

    public FolderNotFoundException(String path){
        super("Diretório de origem não encontrado");
    }
}
