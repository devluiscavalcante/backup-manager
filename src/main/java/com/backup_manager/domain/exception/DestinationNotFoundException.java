package com.backup_manager.domain.exception;

public class DestinationNotFoundException extends RuntimeException{

    public DestinationNotFoundException(){
        super();
    }

    public DestinationNotFoundException(String destinationPath){
        super("Pasta de destino não encontrada");
    }
}
