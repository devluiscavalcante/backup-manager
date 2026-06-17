package com.backup_manager.application.controller;

import com.backup_manager.application.dto.ApiErrorResponse;
import com.backup_manager.application.dto.CollectionResponse;
import com.backup_manager.application.dto.SchemaStatusResponse;
import com.backup_manager.application.dto.StorageDriveResponse;
import com.backup_manager.application.service.SchemaDiagnosticsService;
import com.backup_manager.application.service.SystemStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private static final Logger logger = LoggerFactory.getLogger(SystemController.class);
    private static final String STORAGE_PATH = "/api/system/storage";
    private static final String SCHEMA_PATH = "/api/system/schema";

    private final SystemStorageService storageService;
    private final SchemaDiagnosticsService schemaDiagnosticsService;

    public SystemController(SystemStorageService storageService,
                            SchemaDiagnosticsService schemaDiagnosticsService){
        this.storageService = storageService;
        this.schemaDiagnosticsService = schemaDiagnosticsService;
    }

    @GetMapping("/storage")
    public ResponseEntity<Object> getStorageStats(){
        try {
            return ResponseEntity.ok(CollectionResponse.of(storageService.getStorageInfo()));
        } catch (Exception e) {
            logger.error("Erro ao carregar informacoes de armazenamento", e);
            return internalError(
                    "Nao foi possivel carregar as informacoes de armazenamento.",
                    "system_storage_failed",
                    STORAGE_PATH
            );
        }
    }

    @GetMapping("/schema")
    public ResponseEntity<Object> getSchemaStatus() {
        try {
            return ResponseEntity.ok(schemaDiagnosticsService.inspect());
        } catch (Exception e) {
            logger.error("Erro ao inspecionar schema do banco de dados", e);
            return internalError(
                    "Nao foi possivel inspecionar o schema do banco de dados.",
                    "system_schema_failed",
                    SCHEMA_PATH
            );
        }
    }

    private ResponseEntity<Object> internalError(String message, String code, String path) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(HttpStatus.INTERNAL_SERVER_ERROR, message, code, null, path));
    }
}
