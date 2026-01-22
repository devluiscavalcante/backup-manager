package com.backup_manager.application.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class BackupRequest {

    private List<String> sources;
    private List<String> destination;

}
