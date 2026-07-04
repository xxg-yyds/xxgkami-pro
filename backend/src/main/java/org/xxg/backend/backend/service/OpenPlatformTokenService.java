package org.xxg.backend.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.entity.OpenPlatformToken;
import org.xxg.backend.backend.mapper.OpenPlatformTokenMapper;
import org.xxg.backend.backend.util.OpenPlatformTokenHasher;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OpenPlatformTokenService {

    public static final int MAX_TOKENS_PER_ADMIN = 5;

    private final OpenPlatformTokenMapper tokenMapper;

    public OpenPlatformTokenService(OpenPlatformTokenMapper tokenMapper) {
        this.tokenMapper = tokenMapper;
    }

    public static class CreateTokenResult {
        public final OpenPlatformToken record;
        public final String rawToken;

        public CreateTokenResult(OpenPlatformToken record, String rawToken) {
            this.record = record;
            this.rawToken = rawToken;
        }
    }

    @Transactional
    public CreateTokenResult createToken(Admin admin, String name, String description, LocalDateTime expiresAt) {
        if (name == null || name.isBlank()) {
            throw new RuntimeException("Token 名称不能为空");
        }
        if (admin.getId() == null) {
            throw new RuntimeException("无法识别当前管理员");
        }
        int existing = tokenMapper.countByAdminId(admin.getId());
        if (existing >= MAX_TOKENS_PER_ADMIN) {
            throw new RuntimeException("每位管理员最多可创建 " + MAX_TOKENS_PER_ADMIN + " 个 Token，请先删除不再使用的 Token");
        }
        String raw = OpenPlatformTokenHasher.generateRawToken();
        String hash = OpenPlatformTokenHasher.hashToken(raw);

        OpenPlatformToken entity = new OpenPlatformToken();
        entity.setName(name.trim());
        entity.setDescription(description != null ? description.trim() : null);
        entity.setTokenHash(hash);
        entity.setTokenPrefix(OpenPlatformTokenHasher.displayPrefix(raw));
        entity.setStatus(1);
        entity.setCreatedByAdminId(admin.getId());
        entity.setCreatedByAdminName(admin.getUsername());
        entity.setExpiresAt(expiresAt);

        Long id = tokenMapper.insert(entity);
        entity.setId(id);
        entity.setCreateTime(LocalDateTime.now());
        return new CreateTokenResult(entity, raw);
    }

    public OpenPlatformToken validateAndTouch(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }
        String hash = OpenPlatformTokenHasher.hashToken(rawToken.trim());
        OpenPlatformToken token = tokenMapper.findByHash(hash);
        if (token == null || token.getStatus() == null || token.getStatus() != 1) {
            return null;
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        tokenMapper.updateLastUseTime(token.getId());
        token.setLastUseTime(LocalDateTime.now());
        return token;
    }

    public List<Map<String, Object>> listForAdmin(Admin admin) {
        if (admin.getId() == null) {
            throw new RuntimeException("无法识别当前管理员");
        }
        return tokenMapper.findByAdminId(admin.getId()).stream()
                .map(this::toSafeMap)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteToken(Admin admin, Long id) {
        if (admin.getId() == null || id == null) {
            throw new RuntimeException("参数无效");
        }
        if (tokenMapper.deleteByIdAndAdminId(id, admin.getId()) == 0) {
            throw new RuntimeException("Token 不存在或无权删除");
        }
    }

    public int countForAdmin(Long adminId) {
        if (adminId == null) {
            return 0;
        }
        return tokenMapper.countByAdminId(adminId);
    }

    private Map<String, Object> toSafeMap(OpenPlatformToken t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("name", t.getName());
        m.put("description", t.getDescription());
        m.put("token_prefix", t.getTokenPrefix());
        m.put("status", t.getStatus());
        m.put("created_by_admin_name", t.getCreatedByAdminName());
        m.put("create_time", t.getCreateTime() != null ? t.getCreateTime().toString() : null);
        m.put("last_use_time", t.getLastUseTime() != null ? t.getLastUseTime().toString() : null);
        m.put("expires_at", t.getExpiresAt() != null ? t.getExpiresAt().toString() : null);
        boolean expired = t.getExpiresAt() != null && t.getExpiresAt().isBefore(LocalDateTime.now());
        m.put("expired", expired);
        return m;
    }
}
