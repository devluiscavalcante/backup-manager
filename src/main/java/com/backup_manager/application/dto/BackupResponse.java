package com.backup_manager.application.dto;

import com.backup_manager.domain.model.Status;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class BackupResponse {

    private String sourcePath;
    private String destinationPath;
    private Status status;
    private String errorMessage;
    private Long fileCount;

    private BigDecimal totalSizeMB;


    public void setTotalSizeMB(BigDecimal sizeMB){
        this.totalSizeMB = Objects.requireNonNullElse(sizeMB, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
