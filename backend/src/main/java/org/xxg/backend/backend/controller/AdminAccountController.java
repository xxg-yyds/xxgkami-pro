package org.xxg.backend.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.service.AdminAccountService;
import org.xxg.backend.backend.service.AdminContextService;
import org.xxg.backend.backend.util.AdminPermissions;
import org.xxg.backend.backend.util.ClientIpUtils;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/accounts")
@PreAuthorize("hasRole('ADMIN')")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;
    private final AdminContextService adminContextService;

    public AdminAccountController(AdminAccountService adminAccountService, AdminContextService adminContextService) {
        this.adminAccountService = adminAccountService;
        this.adminContextService = adminContextService;
    }

    @GetMapping("/permissions")
    public ResponseEntity<Map<String, Object>> listPermissionCodes() {
        adminContextService.requirePermission(AdminPermissions.ADMINS);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", AdminPermissions.ALL
        ));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listAdmins() {
        adminContextService.requirePermission(AdminPermissions.ADMINS);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", adminAccountService.listAccounts()
        ));
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createAdmin(@RequestBody Map<String, Object> body, HttpServletRequest request) {
        adminContextService.requirePermission(AdminPermissions.ADMINS);
        Admin operator = adminContextService.requireCurrentAdmin();
        @SuppressWarnings("unchecked")
        List<String> permissions = body.get("permissions") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : List.of();
        Map<String, Object> created = adminAccountService.createAdmin(
                operator,
                stringVal(body.get("username")),
                stringVal(body.get("password")),
                stringVal(body.get("email")),
                Boolean.TRUE.equals(body.get("is_super")),
                permissions,
                ClientIpUtils.resolve(request)
        );
        return ResponseEntity.ok(Map.of("success", true, "data", created));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateAdmin(@PathVariable Long id,
                                                          @RequestBody Map<String, Object> body,
                                                          HttpServletRequest request) {
        adminContextService.requirePermission(AdminPermissions.ADMINS);
        Admin operator = adminContextService.requireCurrentAdmin();
        @SuppressWarnings("unchecked")
        List<String> permissions = body.get("permissions") instanceof List<?> list
                ? list.stream().map(String::valueOf).toList() : null;
        Integer status = body.get("status") != null ? Integer.parseInt(body.get("status").toString()) : null;
        Map<String, Object> updated = adminAccountService.updateAdmin(
                operator,
                id,
                stringVal(body.get("username")),
                stringVal(body.get("password")),
                stringVal(body.get("email")),
                status,
                body.containsKey("is_super") ? Boolean.TRUE.equals(body.get("is_super")) : null,
                permissions,
                ClientIpUtils.resolve(request)
        );
        return ResponseEntity.ok(Map.of("success", true, "data", updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAdmin(@PathVariable Long id, HttpServletRequest request) {
        adminContextService.requirePermission(AdminPermissions.ADMINS);
        Admin operator = adminContextService.requireCurrentAdmin();
        adminAccountService.deleteAdmin(operator, id, ClientIpUtils.resolve(request));
        return ResponseEntity.ok(Map.of("success", true, "message", "已删除"));
    }

    private static String stringVal(Object v) {
        return v == null ? null : v.toString();
    }
}
