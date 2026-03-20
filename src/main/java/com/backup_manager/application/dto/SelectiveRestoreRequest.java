package com.backup_manager.application.dto;

import lombok.Data;

import java.util.List;

@Data
public class SelectiveRestoreRequest {

    private String targetPath;
    private List<String> selectedFiles;
    private boolean overwriteExisting = false;
}