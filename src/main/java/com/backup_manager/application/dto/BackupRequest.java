package com.backup_manager.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BackupRequest {

    @NotEmpty(message = "A lista de origens nao pode estar vazia")
    @Size(max = 20, message = "A lista de origens nao pode ter mais que 20 itens")
    private List<@NotBlank(message = "Origem nao pode estar em branco") String> sources;

    @NotEmpty(message = "A lista de destinos nao pode estar vazia")
    @Size(max = 20, message = "A lista de destinos nao pode ter mais que 20 itens")
    private List<@NotBlank(message = "Destino nao pode estar em branco") String> destination;

}
