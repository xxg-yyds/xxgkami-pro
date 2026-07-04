package org.xxg.backend.backend.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.mapper.AdminMapper;
import org.xxg.backend.backend.util.AdminPermissions;
import org.xxg.backend.backend.util.JwtUtil;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdminContextService {

    private final AdminMapper adminMapper;
    private final JwtUtil jwtUtil;

    public AdminContextService(AdminMapper adminMapper, JwtUtil jwtUtil) {
        this.adminMapper = adminMapper;
        this.jwtUtil = jwtUtil;
    }

    public Admin requireCurrentAdmin() {
        Admin admin = currentAdmin();
        if (admin == null) {
            throw new RuntimeException("未登录或登录已失效");
        }
        if (admin.getStatus() != null && admin.getStatus() == 0) {
            throw new RuntimeException("管理员账号已禁用");
        }
        return admin;
    }

    public Admin currentAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return null;
        }
        return adminMapper.findByUsername(auth.getName());
    }

    public void requirePermission(String permissionCode) {
        Admin admin = requireCurrentAdmin();
        if (!AdminPermissions.has(admin.permissionSet(), admin.isSuperAdmin(), permissionCode)) {
            throw new RuntimeException("无权限访问：" + permissionCode);
        }
    }

    public Map<String, Object> toUserInfo(Admin admin) {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("id", admin.getId());
        info.put("username", admin.getUsername());
        info.put("role", "admin");
        info.put("totpEnabled", admin.getTotpEnabled() != null && admin.getTotpEnabled());
        info.put("isSuper", admin.isSuperAdmin());
        info.put("permissions", List.copyOf(admin.permissionSet()));
        return info;
    }

    public String generateAccessToken(Admin admin) {
        return jwtUtil.generateAdminToken(
                admin.getUsername(),
                admin.getId(),
                admin.isSuperAdmin(),
                AdminPermissions.join(admin.permissionSet())
        );
    }

    public String generateRefreshToken(Admin admin) {
        return jwtUtil.generateAdminRefreshToken(
                admin.getUsername(),
                admin.getId(),
                admin.isSuperAdmin(),
                AdminPermissions.join(admin.permissionSet())
        );
    }
}
