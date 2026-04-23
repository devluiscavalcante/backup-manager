package com.backup_manager.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RestoreRequest {

    @NotBlank(message = "O caminho de destino nao pode estar vazio")
    private String targetPath;
    private boolean overwriteExisting = false;
}
