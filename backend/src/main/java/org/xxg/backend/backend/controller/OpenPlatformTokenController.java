package org.xxg.backend.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.entity.OpenPlatformToken;
import org.xxg.backend.backend.service.AdminContextService;
import org.xxg.backend.backend.service.AdminLogService;
import org.xxg.backend.backend.service.OpenPlatformTokenService;
import org.xxg.backend.backend.util.AdminPermissions;
import org.xxg.backend.backend.util.ClientIpUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/open-platform-tokens")
@CrossOrigin
public class OpenPlatformTokenController {

    private final OpenPlatformTokenService tokenService;
    private final AdminContextService adminContextService;
    private final AdminLogService adminLogService;

    public OpenPlatformTokenController(
            OpenPlatformTokenService tokenService,
            AdminContextService adminContextService,
            AdminLogService adminLogService
    ) {
        this.tokenService = tokenService;
        this.adminContextService = adminContextService;
        this.adminLogService = adminLogService;
    }

    private Admin requireApiAdmin() {
        adminContextService.requirePermission(AdminPermissions.API);
        return adminContextService.requireCurrentAdmin();
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        Admin admin = requireApiAdmin();
        List<Map<String, Object>> data = tokenService.listForAdmin(admin);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", data,
                "limit", OpenPlatformTokenService.MAX_TOKENS_PER_ADMIN,
                "count", data.size()
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request
    ) {
        Admin admin = requireApiAdmin();
        String name = body.get("name") != null ? String.valueOf(body.get("name")).trim() : null;
        String description = body.get("description") != null ? String.valueOf(body.get("description")).trim() : null;
        LocalDateTime expiresAt = null;
        Object exp = body.get("expires_at");
        if (exp != null && !String.valueOf(exp).isBlank()) {
            try {
                expiresAt = LocalDateTime.parse(String.valueOf(exp).trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "expires_at 格式须为 ISO 日期时间"));
            }
        }

        OpenPlatformTokenService.CreateTokenResult result;
        try {
            result = tokenService.createToken(admin, name, description, expiresAt);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
        OpenPlatformToken record = result.record;

        adminLogService.log(admin, "open_token_create", "创建开放平台 Token: " + name, ClientIpUtils.resolve(request));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", record.getId());
        data.put("name", record.getName());
        data.put("description", record.getDescription());
        data.put("token_prefix", record.getTokenPrefix());
        data.put("token", result.rawToken);
        data.put("create_time", record.getCreateTime() != null ? record.getCreateTime().toString() : null);
        data.put("expires_at", record.getExpiresAt() != null ? record.getExpiresAt().toString() : null);
        data.put("view_once_hint", "Token 仅显示一次，请立即复制保存");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Token 创建成功",
                "data", data
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id, HttpServletRequest request) {
        Admin admin = requireApiAdmin();
        tokenService.deleteToken(admin, id);
        adminLogService.log(admin, "open_token_delete", "删除开放平台 Token id=" + id, ClientIpUtils.resolve(request));
        return ResponseEntity.ok(Map.of("success", true, "message", "Token 已删除"));
    }
}
