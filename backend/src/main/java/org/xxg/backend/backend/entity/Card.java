package org.xxg.backend.backend.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;

/**
 * 卡密实体类
 */
public class Card {
    private Long id;
    
    @JsonProperty("card_key")
    private String cardKey;
    
    @JsonProperty("encrypted_key")
    private String encryptedKey;
    
    private Integer status; // 0:未使用 1:已使用 2:已暂停(管理员) 4:已合并(时间卡续期到其它卡)
    
    @JsonProperty("create_time")
    private LocalDateTime createTime;
    
    @JsonProperty("use_time")
    private LocalDateTime useTime;
    
    @JsonProperty("expire_time")
    private LocalDateTime expireTime;
    
    private Integer duration;

    /** 时间卡时长单位：days（默认）或 hours */
    @JsonProperty("duration_unit")
    private String durationUnit;
    
    @JsonProperty("verify_method")
    private String verifyMethod;
    
    @JsonProperty("allow_reverify")
    private Integer allowReverify;
    
    @JsonProperty("device_id")
    private String deviceId;
    
    @JsonProperty("ip_address")
    private String ipAddress;
    
    @JsonProperty("encryption_type")
    private String encryptionType;
    
    @JsonProperty("card_type")
    private String cardType;
    
    @JsonProperty("total_count")
    private Integer totalCount;
    
    @JsonProperty("remaining_count")
    private Integer remainingCount;
    
    @JsonProperty("creator_type")
    private String creatorType;
    
    @JsonProperty("creator_id")
    private Long creatorId;
    
    @JsonProperty("creator_name")
    private String creatorName;

    @JsonProperty("api_key_id")
    private Long apiKeyId;

    @JsonProperty("machine_code")
    private String machineCode;

    /** true：同机器码上若已有未过期时间卡，核销本卡时将时长叠加到该卡的到期时间（单张=false 时沿用现行「从当前时刻起算」逻辑） */
    @JsonProperty("stack_time_if_same_machine")
    private Boolean stackTimeIfSameMachine;

    /** true：允许持卡用户在首页「在线解绑」自助清空机器码与设备绑定 */
    @JsonProperty("allow_self_unbind")
    private Boolean allowSelfUnbind;

    /** true：自助解绑时须验证原设备码（机器码）一致 */
    @JsonProperty("require_device_unbind")
    private Boolean requireDeviceUnbind;

    /** 自助解绑冷却间隔（小时），0 表示不限制 */
    @JsonProperty("unbind_cooldown_hours")
    private Integer unbindCooldownHours;

    /** 自助解绑次数上限，0 表示不限制 */
    @JsonProperty("unbind_max_count")
    private Integer unbindMaxCount;

    /** 已累计自助解绑次数 */
    @JsonProperty("unbind_count")
    private Integer unbindCount;

    /** 最近一次自助解绑时间（用于冷却校验） */
    @JsonProperty("last_unbind_time")
    private LocalDateTime lastUnbindTime;

    /** 若为已合并卡，指向被续期的主卡 rows */
    @JsonProperty("merged_into_card_id")
    private Long mergedIntoCardId;

    /** encrypted=主表 cards；simple=simple_cards 表 */
    @JsonProperty("storage_type")
    private String storageType;

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCardKey() { return cardKey; }
    public void setCardKey(String cardKey) { this.cardKey = cardKey; }

    public String getEncryptedKey() { return encryptedKey; }
    public void setEncryptedKey(String encryptedKey) { this.encryptedKey = encryptedKey; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUseTime() { return useTime; }
    public void setUseTime(LocalDateTime useTime) { this.useTime = useTime; }

    public LocalDateTime getExpireTime() { return expireTime; }
    public void setExpireTime(LocalDateTime expireTime) { this.expireTime = expireTime; }

    public Integer getDuration() { return duration; }
    public void setDuration(Integer duration) { this.duration = duration; }

    public String getDurationUnit() { return durationUnit; }
    public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }

    public String getVerifyMethod() { return verifyMethod; }
    public void setVerifyMethod(String verifyMethod) { this.verifyMethod = verifyMethod; }

    public Integer getAllowReverify() { return allowReverify; }
    public void setAllowReverify(Integer allowReverify) { this.allowReverify = allowReverify; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public String getEncryptionType() { return encryptionType; }
    public void setEncryptionType(String encryptionType) { this.encryptionType = encryptionType; }

    public String getCardType() { return cardType; }
    public void setCardType(String cardType) { this.cardType = cardType; }

    public Integer getTotalCount() { return totalCount; }
    public void setTotalCount(Integer totalCount) { this.totalCount = totalCount; }

    public Integer getRemainingCount() { return remainingCount; }
    public void setRemainingCount(Integer remainingCount) { this.remainingCount = remainingCount; }

    public String getCreatorType() { return creatorType; }
    public void setCreatorType(String creatorType) { this.creatorType = creatorType; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public String getCreatorName() { return creatorName; }
    public void setCreatorName(String creatorName) { this.creatorName = creatorName; }

    public Long getApiKeyId() { return apiKeyId; }
    public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }

    public String getMachineCode() { return machineCode; }
    public void setMachineCode(String machineCode) { this.machineCode = machineCode; }

    public Boolean getStackTimeIfSameMachine() { return stackTimeIfSameMachine; }
    public void setStackTimeIfSameMachine(Boolean stackTimeIfSameMachine) { this.stackTimeIfSameMachine = stackTimeIfSameMachine; }

    public Boolean getAllowSelfUnbind() { return allowSelfUnbind; }
    public void setAllowSelfUnbind(Boolean allowSelfUnbind) { this.allowSelfUnbind = allowSelfUnbind; }

    public Boolean getRequireDeviceUnbind() { return requireDeviceUnbind; }
    public void setRequireDeviceUnbind(Boolean requireDeviceUnbind) { this.requireDeviceUnbind = requireDeviceUnbind; }

    public Integer getUnbindCooldownHours() { return unbindCooldownHours; }
    public void setUnbindCooldownHours(Integer unbindCooldownHours) { this.unbindCooldownHours = unbindCooldownHours; }

    public Integer getUnbindMaxCount() { return unbindMaxCount; }
    public void setUnbindMaxCount(Integer unbindMaxCount) { this.unbindMaxCount = unbindMaxCount; }

    public Integer getUnbindCount() { return unbindCount; }
    public void setUnbindCount(Integer unbindCount) { this.unbindCount = unbindCount; }

    public LocalDateTime getLastUnbindTime() { return lastUnbindTime; }
    public void setLastUnbindTime(LocalDateTime lastUnbindTime) { this.lastUnbindTime = lastUnbindTime; }

    public Long getMergedIntoCardId() { return mergedIntoCardId; }
    public void setMergedIntoCardId(Long mergedIntoCardId) { this.mergedIntoCardId = mergedIntoCardId; }

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
}
