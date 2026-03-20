package com.backup_manager.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileNodeDTO {

    private String name;
    private String relativePath;
    private String type;
    private BigDecimal sizeMB;
    private LocalDateTime lastModified;
    private Long fileCount;
    private List<FileNodeDTO> children;
}