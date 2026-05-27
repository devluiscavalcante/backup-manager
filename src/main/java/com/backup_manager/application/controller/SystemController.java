package com.backup_manager.application.controller;

import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.StorageDriveResponse;
import com.backup_manager.application.service.SystemStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStorageService storageService;

    public SystemController(SystemStorageService storageService){
        this.storageService = storageService;
    }

    @GetMapping("/storage")
    public ResponseEntity<CollectionResponse<StorageDriveResponse>> getStorageStats(){
        return ResponseEntity.ok(CollectionResponse.of(storageService.getStorageInfo()));
    }
}
