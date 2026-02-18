package com.backup_manager.application.controller;

import com.backup_manager.application.service.SystemStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class SystemController {

    private final SystemStorageService storageService;

    public SystemController(SystemStorageService storageService){
        this.storageService = storageService;
    }

    @GetMapping("/storage")
    public ResponseEntity<List<Map<String, Object>>> getStorageStats(){
        return ResponseEntity.ok(storageService.getStorageInfo());
    }
}
