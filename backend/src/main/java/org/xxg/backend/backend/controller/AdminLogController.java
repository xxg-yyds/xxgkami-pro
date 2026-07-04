package org.xxg.backend.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.service.AdminContextService;
import org.xxg.backend.backend.service.AdminLogService;
import org.xxg.backend.backend.util.AdminPermissions;

import java.util.Map;

@RestController
@RequestMapping("/admin/logs")
@PreAuthorize("hasRole('ADMIN')")
public class AdminLogController {

    private final AdminLogService adminLogService;
    private final AdminContextService adminContextService;

    public AdminLogController(AdminLogService adminLogService, AdminContextService adminContextService) {
        this.adminLogService = adminLogService;
        this.adminContextService = adminContextService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listLogs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "operation_type") String operationType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        adminContextService.requirePermission(AdminPermissions.LOGS);
        Map<String, Object> data = adminLogService.list(keyword, operationType, page, pageSize);
        return ResponseEntity.ok(Map.of("success", true, "data", data));
    }
}
