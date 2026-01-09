package com.backup_manager.domain.exception;

public class FolderEmptyException extends RuntimeException{

    public FolderEmptyException(String path){
        super("A pasta de origem está vazia");
    }
}
