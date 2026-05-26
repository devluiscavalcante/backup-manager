package com.backup_manager.application.service;

import com.backup_manager.application.dto.StorageDriveResponse;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemStorageService {

    public List<StorageDriveResponse> getStorageInfo() {
        List<StorageDriveResponse> drivesInfo = new ArrayList<>();
        File[] roots = File.listRoots();

        for (File root : roots) {

            if (root.getTotalSpace() == 0) continue;

            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            double usagePercent = ((double) usedSpace / totalSpace) * 100;

            drivesInfo.add(new StorageDriveResponse(
                    root.getAbsolutePath(),
                    formatGB(totalSpace),
                    formatGB(usableSpace),
                    formatGB(usedSpace),
                    Math.round(usagePercent),
                    usagePercent > 90
            ));
        }
        return drivesInfo;
    }

    private long formatGB(long bytes) {
        return bytes / (1024 * 1024 * 1024);
    }
}
