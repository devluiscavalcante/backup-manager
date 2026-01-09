package com.backup_manager.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BackupTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String sourcePath;
    private String destinationPath;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long fileCount;
    private BigDecimal totalSizeMB;

    @Enumerated(EnumType.STRING)
    private Status status = Status.EM_ANDAMENTO;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String errorMessage;

    @PrePersist
    public void prePersist(){
        if(status == null){
            status = Status.EM_ANDAMENTO;
        }
        if(startedAt == null){
            startedAt = LocalDateTime.now();
        }
    }
}
