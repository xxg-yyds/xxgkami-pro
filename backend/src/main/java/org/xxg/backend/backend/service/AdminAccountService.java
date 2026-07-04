package org.xxg.backend.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.mapper.AdminMapper;
import org.xxg.backend.backend.util.AdminPermissions;
import org.xxg.backend.backend.util.PasswordUtil;

import java.util.*;

@Service
public class AdminAccountService {

    private final AdminMapper adminMapper;
    private final AdminLogService adminLogService;

    public AdminAccountService(AdminMapper adminMapper, AdminLogService adminLogService) {
        this.adminMapper = adminMapper;
        this.adminLogService = adminLogService;
    }

    public List<Map<String, Object>> listAccounts() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Admin admin : adminMapper.findAll()) {
            result.add(toSafeView(admin));
        }
        return result;
    }

    public Map<String, Object> toSafeView(Admin admin) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", admin.getId());
        view.put("username", admin.getUsername());
        view.put("email", admin.getEmail());
        view.put("status", admin.getStatus() != null ? admin.getStatus() : 1);
        view.put("is_super", admin.isSuperAdmin());
        view.put("permissions", new ArrayList<>(admin.permissionSet()));
        view.put("create_time", admin.getCreateTime());
        view.put("last_login", admin.getLastLogin());
        view.put("totp_enabled", admin.getTotpEnabled() != null && admin.getTotpEnabled());
        return view;
    }

    @Transactional
    public Map<String, Object> createAdmin(Admin operator, String username, String password, String email,
                                           boolean isSuper, List<String> permissions, String ip) {
        if (username == null || username.isBlank()) {
            throw new RuntimeException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new RuntimeException("密码至少 6 位");
        }
        if (adminMapper.findByUsername(username.trim()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        Admin admin = new Admin();
        admin.setUsername(username.trim());
        admin.setPassword(PasswordUtil.hashPassword(password));
        admin.setEmail(email != null ? email.trim() : null);
        admin.setStatus(1);
        admin.setCreatedBy(operator.getId());
        applyRole(admin, operator, isSuper, permissions);
        Long id = adminMapper.insertAdmin(admin);
        admin.setId(id);
        adminLogService.log(operator, "admin_create", "创建管理员：" + admin.getUsername(), ip);
        return toSafeView(admin);
    }

    @Transactional
    public Map<String, Object> updateAdmin(Admin operator, Long targetId, String username, String password, String email,
                                           Integer status, Boolean isSuper, List<String> permissions, String ip) {
        Admin target = adminMapper.findById(targetId);
        if (target == null) {
            throw new RuntimeException("管理员不存在");
        }
        guardTarget(operator, target);
        if (username != null && !username.isBlank() && !username.equals(target.getUsername())) {
            Admin existing = adminMapper.findByUsername(username.trim());
            if (existing != null && !existing.getId().equals(targetId)) {
                throw new RuntimeException("用户名已存在");
            }
            target.setUsername(username.trim());
        }
        if (password != null && !password.isBlank()) {
            if (password.length() < 6) {
                throw new RuntimeException("密码至少 6 位");
            }
            target.setPassword(PasswordUtil.hashPassword(password));
        }
        if (email != null) {
            target.setEmail(email.trim());
        }
        if (status != null) {
            if (status == 0 && target.isSuperAdmin() && countEnabledSuperAdmins() <= 1) {
                throw new RuntimeException("不能禁用唯一的超级管理员");
            }
            target.setStatus(status);
        }
        if (isSuper != null || permissions != null) {
            applyRole(target, operator, isSuper != null ? isSuper : target.isSuperAdmin(), permissions);
        }
        adminMapper.updateAdmin(target);
        adminLogService.log(operator, "admin_update", "更新管理员：" + target.getUsername(), ip);
        return toSafeView(target);
    }

    @Transactional
    public void deleteAdmin(Admin operator, Long targetId, String ip) {
        Admin target = adminMapper.findById(targetId);
        if (target == null) {
            throw new RuntimeException("管理员不存在");
        }
        guardTarget(operator, target);
        if (target.getId().equals(operator.getId())) {
            throw new RuntimeException("不能删除当前登录账号");
        }
        if (target.isSuperAdmin() && countEnabledSuperAdmins() <= 1) {
            throw new RuntimeException("不能删除唯一的超级管理员");
        }
        if (adminMapper.countAll() <= 1) {
            throw new RuntimeException("系统至少保留一名管理员");
        }
        adminMapper.deleteById(targetId);
        adminLogService.log(operator, "admin_delete", "删除管理员：" + target.getUsername(), ip);
    }

    private void applyRole(Admin target, Admin operator, boolean isSuper, List<String> permissions) {
        if (isSuper) {
            if (!operator.isSuperAdmin()) {
                throw new RuntimeException("仅超级管理员可设置超级管理员");
            }
            target.setIsSuper(true);
            target.setPermissions(AdminPermissions.join(AdminPermissions.allSet()));
            return;
        }
        target.setIsSuper(false);
        Set<String> sanitized = AdminPermissions.sanitize(new LinkedHashSet<>(permissions != null ? permissions : List.of()));
        if (sanitized.isEmpty()) {
            throw new RuntimeException("请至少分配一项权限");
        }
        if (!operator.isSuperAdmin()) {
            Set<String> operatorPerms = operator.permissionSet();
            for (String code : sanitized) {
                if (!operatorPerms.contains(code)) {
                    throw new RuntimeException("不能分配您未拥有的权限：" + code);
                }
            }
            if (sanitized.contains(AdminPermissions.ADMINS)) {
                throw new RuntimeException("仅超级管理员可分配「管理员管理」权限");
            }
        }
        target.setPermissions(AdminPermissions.join(sanitized));
    }

    private void guardTarget(Admin operator, Admin target) {
        if (!operator.isSuperAdmin() && target.isSuperAdmin()) {
            throw new RuntimeException("无权操作超级管理员");
        }
        if (!operator.isSuperAdmin() && !operator.getId().equals(target.getId())) {
            throw new RuntimeException("仅超级管理员可管理其它管理员");
        }
    }

    private int countEnabledSuperAdmins() {
        int count = 0;
        for (Admin admin : adminMapper.findAll()) {
            if (admin.isSuperAdmin() && (admin.getStatus() == null || admin.getStatus() == 1)) {
                count++;
            }
        }
        return count;
    }
}
