package org.xxg.backend.backend.entity;

import java.time.LocalDateTime;

public class OpenPlatformToken {
    private Long id;
    private String name;
    private String description;
    /** SHA-256 哈希，不存明文 */
    private String tokenHash;
    /** 明文前缀，便于识别，如 xxg_a1b2**** */
    private String tokenPrefix;
    private Integer status;
    private Long createdByAdminId;
    private String createdByAdminName;
    private LocalDateTime createTime;
    private LocalDateTime lastUseTime;
    private LocalDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTokenHash() { return tokenHash; }
    public void setTokenHash(String tokenHash) { this.tokenHash = tokenHash; }

    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Long getCreatedByAdminId() { return createdByAdminId; }
    public void setCreatedByAdminId(Long createdByAdminId) { this.createdByAdminId = createdByAdminId; }

    public String getCreatedByAdminName() { return createdByAdminName; }
    public void setCreatedByAdminName(String createdByAdminName) { this.createdByAdminName = createdByAdminName; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getLastUseTime() { return lastUseTime; }
    public void setLastUseTime(LocalDateTime lastUseTime) { this.lastUseTime = lastUseTime; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
