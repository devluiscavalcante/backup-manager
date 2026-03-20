package com.backup_manager.application.dto;

import lombok.Data;

@Data
public class RestoreRequest {

    private String targetPath;
    private boolean overwriteExisting = false;
}