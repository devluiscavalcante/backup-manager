package com.backup_manager.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class ScheduledBackupRequest {

    private Long id;

    @NotBlank(message = "O nome do agendamento nao pode estar vazio")
    private String name;

    @NotEmpty(message = "A lista de origens nao pode estar vazia")
    @Size(max = 20, message = "A lista de origens nao pode ter mais que 20 itens")
    private List<@NotBlank(message = "Origem nao pode estar em branco") String> sources;

    @NotEmpty(message = "A lista de destinos nao pode estar vazia")
    @Size(max = 20, message = "A lista de destinos nao pode ter mais que 20 itens")
    private List<@NotBlank(message = "Destino nao pode estar em branco") String> destinations;

    @NotBlank(message = "A expressao cron nao pode estar vazia")
    private String cronExpression;

    private Boolean enabled;
}
