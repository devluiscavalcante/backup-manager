package com.backup_manager.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class SelectiveRestoreRequest {

    @NotBlank(message = "O caminho de destino nao pode estar vazio")
    private String targetPath;
    @NotEmpty(message = "A lista de arquivos selecionados nao pode estar vazia")
    private List<@NotBlank(message = "Arquivo selecionado nao pode estar em branco") String> selectedFiles;
    private boolean overwriteExisting = false;
}
