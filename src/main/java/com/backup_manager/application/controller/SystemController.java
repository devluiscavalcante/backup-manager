package com.backup_manager.application.controller;

import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.SchemaStatusResponse;
import com.backup_manager.application.dto.StorageDriveResponse;
import com.backup_manager.application.service.SchemaDiagnosticsService;
import com.backup_manager.application.service.SystemStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStorageService storageService;
    private final SchemaDiagnosticsService schemaDiagnosticsService;

    public SystemController(SystemStorageService storageService,
                            SchemaDiagnosticsService schemaDiagnosticsService){
        this.storageService = storageService;
        this.schemaDiagnosticsService = schemaDiagnosticsService;
    }

    @GetMapping("/storage")
    public ResponseEntity<CollectionResponse<StorageDriveResponse>> getStorageStats(){
        return ResponseEntity.ok(CollectionResponse.of(storageService.getStorageInfo()));
    }

    @GetMapping("/schema")
    public ResponseEntity<SchemaStatusResponse> getSchemaStatus() {
        return ResponseEntity.ok(schemaDiagnosticsService.inspect());
    }
}
