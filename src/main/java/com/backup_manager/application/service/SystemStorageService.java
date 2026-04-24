package com.backup_manager.application.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemStorageService {

    public List<Map<String, Object>> getStorageInfo() {
        List<Map<String, Object>> drivesInfo = new ArrayList<>();
        File[] roots = File.listRoots();

        for (File root : roots) {

            if (root.getTotalSpace() == 0) continue;

            Map<String, Object> drive = new HashMap<>();

            long totalSpace = root.getTotalSpace();
            long usableSpace = root.getUsableSpace();
            long usedSpace = totalSpace - usableSpace;

            double usagePercent = ((double) usedSpace / totalSpace) * 100;

            drive.put("driveLetter", root.getAbsolutePath());
            drive.put("totalSpaceGB", formatGB(totalSpace));
            drive.put("freeSpaceGB", formatGB(usableSpace));
            drive.put("usedSpaceGB", formatGB(usedSpace));
            drive.put("usagePercent", Math.round(usagePercent));
            drive.put("isCritical", usagePercent > 90);

            drivesInfo.add(drive);
        }
        return drivesInfo;
    }

    private long formatGB(long bytes) {
        return bytes / (1024 * 1024 * 1024);
    }
}
