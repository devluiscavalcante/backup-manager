package com.backup_manager.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class StorageDriveResponse {

    private final String driveLetter;
    private final long totalSpaceGB;
    private final long freeSpaceGB;
    private final long usedSpaceGB;
    private final long usagePercent;
    @JsonProperty("isCritical")
    private final boolean isCritical;
}
