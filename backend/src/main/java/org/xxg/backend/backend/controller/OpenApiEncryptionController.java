package org.xxg.backend.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.service.AdminContextService;
import org.xxg.backend.backend.service.AdminLogService;
import org.xxg.backend.backend.service.OpenApiParamEncryptionService;
import org.xxg.backend.backend.util.AdminPermissions;
import org.xxg.backend.backend.util.ClientIpUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/open-api-encryption")
@CrossOrigin
public class OpenApiEncryptionController {

    private final OpenApiParamEncryptionService encryptionService;
    private final AdminContextService adminContextService;
    private final AdminLogService adminLogService;

    public OpenApiEncryptionController(
            OpenApiParamEncryptionService encryptionService,
            AdminContextService adminContextService,
            AdminLogService adminLogService
    ) {
        this.encryptionService = encryptionService;
        this.adminContextService = adminContextService;
        this.adminLogService = adminLogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        adminContextService.requirePermission(AdminPermissions.API);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", encryptionService.getAdminConfig()
        ));
    }

    @PutMapping
    public ResponseEntity<Map<String, Object>> saveConfig(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        adminContextService.requirePermission(AdminPermissions.API);

        Boolean requestEnabled = null;
        if (body.containsKey("request_enabled")) {
            requestEnabled = Boolean.TRUE.equals(body.get("request_enabled"));
        } else if (body.containsKey("enabled")) {
            requestEnabled = Boolean.TRUE.equals(body.get("enabled"));
        }

        Boolean responseEnabled = null;
        if (body.containsKey("response_enabled")) {
            responseEnabled = Boolean.TRUE.equals(body.get("response_enabled"));
        }

        boolean regenerateKey = Boolean.TRUE.equals(body.get("regenerate_key"));
        Map<String, Object> data = encryptionService.saveAdminConfig(requestEnabled, responseEnabled, regenerateKey);

        List<String> changes = new ArrayList<>();
        if (requestEnabled != null) {
            changes.add(requestEnabled ? "开启入参加密" : "关闭入参加密");
        }
        if (responseEnabled != null) {
            changes.add(responseEnabled ? "开启出参加密" : "关闭出参加密");
        }
        if (regenerateKey) {
            changes.add("重新生成 Key/IV");
        }
        if (!changes.isEmpty()) {
            adminLogService.log(
                    adminContextService.requireCurrentAdmin(),
                    "open_api_encryption",
                    String.join("；", changes),
                    ClientIpUtils.resolve(request)
            );
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "加密配置已保存",
                "data", data
        ));
    }
}
