package org.xxg.backend.backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.xxg.backend.backend.util.ClientIpUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.entity.ApiKey;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.entity.Card;
import org.xxg.backend.backend.service.AdminContextService;
import org.xxg.backend.backend.service.AdminLogService;
import org.xxg.backend.backend.service.ApiKeyService;
import org.xxg.backend.backend.service.CardService;
import org.xxg.backend.backend.util.AdminPermissions;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xxg.backend.backend.util.CustomCardObfuscator;

/**
 * 卡密控制器
 */
@RestController
@RequestMapping("/cards")
public class CardController {

    @Autowired
    private CardService cardService;

    @Autowired
    private ApiKeyService apiKeyService;
    
    @Autowired
    private CustomCardObfuscator customCardObfuscator;

    @Autowired
    private AdminContextService adminContextService;

    @Autowired
    private AdminLogService adminLogService;

    private Admin requireKeysAdmin() {
        adminContextService.requirePermission(AdminPermissions.KEYS);
        return adminContextService.requireCurrentAdmin();
    }

    /**
     * 获取用户的卡密
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserCards(@PathVariable Long userId) {
        try {
            List<Card> cards = cardService.getUserCards(userId);
            return ResponseEntity.ok(Map.of("success", true, "data", cards));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员批量创建卡密
     */
    @PostMapping("/admin/create")
    public ResponseEntity<Map<String, Object>> createCards(@RequestBody CreateCardRequest request, HttpServletRequest httpRequest) {
        try {
            Admin admin = requireKeysAdmin();
            Long adminId = admin.getId();
            String adminName = admin.getUsername();
            String creatorType = "admin";

            String durationUnit = request.getDurationUnit() != null ? request.getDurationUnit() : "days";
            if ("time".equals(request.getCardType()) && !CardService.isPermanentUnit(durationUnit)) {
                CardService.validateDurationForUnit(request.getDuration(), durationUnit);
            }

            List<Card> cards;
            if (Boolean.FALSE.equals(request.getUseEncrypted())) {
                cards = cardService.createSimpleCards(
                        request.getCount(),
                        request.getCardType(),
                        request.getDuration(),
                        request.getTotalCount(),
                        request.getVerifyMethod(),
                        request.getAllowReverify(),
                        creatorType,
                        adminId,
                        adminName,
                        request.getApiKeyId(),
                        Boolean.TRUE.equals(request.getStackTimeIfSameMachine()),
                        Boolean.TRUE.equals(request.getAllowSelfUnbind()),
                        request.getKeyLength() != null ? request.getKeyLength() : 16,
                        request.getManualCardKeys(),
                        request.getKeyPrefix(),
                        durationUnit,
                        request.getRequireDeviceUnbind(),
                        request.getUnbindCooldownHours(),
                        request.getUnbindMaxCount()
                );
            } else {
                String encType = request.getEncryptionType();
                if (encType == null || encType.isEmpty()) {
                    encType = "advanced";
                }
                cards = cardService.createCards(
                        request.getCount(),
                        request.getCardType(),
                        request.getDuration(),
                        request.getTotalCount(),
                        request.getVerifyMethod(),
                        encType,
                        request.getAllowReverify(),
                        creatorType,
                        adminId,
                        adminName,
                        request.getApiKeyId(),
                        Boolean.TRUE.equals(request.getStackTimeIfSameMachine()),
                        Boolean.TRUE.equals(request.getAllowSelfUnbind()),
                        durationUnit,
                        request.getRequireDeviceUnbind(),
                        request.getUnbindCooldownHours(),
                        request.getUnbindMaxCount()
                );
            }
            adminLogService.log(admin, "card_create", "创建卡密 " + cards.size() + " 条", ClientIpUtils.resolve(httpRequest));
            return ResponseEntity.ok(Map.of("success", true, "data", cards));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/import")
    public ResponseEntity<Map<String, Object>> importCards(@RequestBody CreateCardRequest request, HttpServletRequest httpRequest) {
        try {
            Admin admin = requireKeysAdmin();
            List<Card> cards;
            if (request.getImportItems() != null && !request.getImportItems().isEmpty()) {
                cards = cardService.importSimpleCards(
                        request.getImportItems(),
                        request.getVerifyMethod(),
                        request.getAllowReverify(),
                        "admin",
                        admin.getId(),
                        admin.getUsername(),
                        request.getApiKeyId(),
                        Boolean.TRUE.equals(request.getStackTimeIfSameMachine()),
                        Boolean.TRUE.equals(request.getAllowSelfUnbind()),
                        request.getRequireDeviceUnbind(),
                        request.getUnbindCooldownHours(),
                        request.getUnbindMaxCount()
                );
            } else {
                if (request.getManualCardKeys() == null || request.getManualCardKeys().isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请提供要导入的卡密列表 card_keys"));
                }
                List<String> keys = request.getManualCardKeys().stream()
                        .filter(k -> k != null && !k.isBlank())
                        .map(String::trim)
                        .distinct()
                        .toList();
                if (keys.isEmpty()) {
                    return ResponseEntity.badRequest().body(Map.of("success", false, "message", "没有有效的卡密"));
                }
                request.setCount(keys.size());
                request.setUseEncrypted(false);
                request.setManualCardKeys(keys);

                String durationUnit = request.getDurationUnit() != null ? request.getDurationUnit() : "days";
                if ("time".equals(request.getCardType()) && !CardService.isPermanentUnit(durationUnit)) {
                    CardService.validateDurationForUnit(request.getDuration(), durationUnit);
                }
                cards = cardService.createSimpleCards(
                        keys.size(),
                        request.getCardType(),
                        request.getDuration(),
                        request.getTotalCount(),
                        request.getVerifyMethod(),
                        request.getAllowReverify(),
                        "admin",
                        admin.getId(),
                        admin.getUsername(),
                        request.getApiKeyId(),
                        Boolean.TRUE.equals(request.getStackTimeIfSameMachine()),
                        Boolean.TRUE.equals(request.getAllowSelfUnbind()),
                        16,
                        keys,
                        null,
                        durationUnit,
                        request.getRequireDeviceUnbind(),
                        request.getUnbindCooldownHours(),
                        request.getUnbindMaxCount()
                );
            }
            adminLogService.log(admin, "card_import", "导入卡密 " + cards.size() + " 条", ClientIpUtils.resolve(httpRequest));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", cards,
                    "imported", cards.size(),
                    "message", "成功导入 " + cards.size() + " 条卡密"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员获取所有卡密
     */
    @GetMapping("/admin/all")
    public ResponseEntity<Map<String, Object>> getAllCards() {
        try {
            adminContextService.requirePermission(AdminPermissions.KEYS);
            List<Card> cards = cardService.getAllCards();
            return ResponseEntity.ok(Map.of("success", true, "data", cards));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/apikey/{apiKeyId}")
    public ResponseEntity<Map<String, Object>> getApiKeyCards(@PathVariable Long apiKeyId) {
        try {
            List<Card> cards = cardService.getCardsByApiKey(apiKeyId);
            return ResponseEntity.ok(Map.of("success", true, "data", cards));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员编辑卡密（含机器码重置）
     */
    @PutMapping("/admin/{id}")
    public ResponseEntity<Map<String, Object>> updateCard(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body,
            HttpServletRequest httpRequest) {
        try {
            Admin admin = requireKeysAdmin();
            cardService.adminUpdateCard(id, body);
            adminLogService.log(admin, "card_update", "更新卡密 ID=" + id, ClientIpUtils.resolve(httpRequest));
            return ResponseEntity.ok(Map.of("success", true, "message", "卡密更新成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员更新卡密启用/暂停状态（status: 2=暂停，1=恢复启用）
     */
    @PatchMapping("/admin/{id}/status")
    public ResponseEntity<Map<String, Object>> updateAdminCardStatus(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Integer status = null;
            if (body != null && body.get("status") instanceof Number num) {
                status = num.intValue();
            }
            if (status == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "缺少 status"));
            }
            String storageType = body != null && body.get("storage_type") != null
                    ? body.get("storage_type").toString() : "encrypted";
            String msg = cardService.updateAdminCardStatus(id, status, storageType);
            return ResponseEntity.ok(Map.of("success", true, "message", msg));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 删除卡密
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCard(
            @PathVariable Long id,
            @RequestParam(value = "storage_type", defaultValue = "encrypted") String storageType) {
        try {
            cardService.deleteCard(id, storageType);
            return ResponseEntity.ok(Map.of("success", true, "message", "卡密删除成功"));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员批量删除卡密（可含已使用、已过期、已暂停等；无效 id 自动跳过）
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/admin/batch-delete")
    public ResponseEntity<Map<String, Object>> deleteCardsBatch(@RequestBody(required = false) Map<String, Object> body) {
        try {
            if (body == null || !(body.get("ids") instanceof List<?>)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "请提供 ids 数组"));
            }
            List<?> rawList = (List<?>) body.get("ids");
            List<Long> ids = new ArrayList<>();
            List<String> storageTypes = new ArrayList<>();
            for (Object o : rawList) {
                if (o instanceof Number num) {
                    ids.add(num.longValue());
                    storageTypes.add("encrypted");
                } else if (o instanceof String str && !str.isBlank()) {
                    try {
                        ids.add(Long.parseLong(str.trim()));
                        storageTypes.add("encrypted");
                    } catch (NumberFormatException ignored) {
                        // skip
                    }
                } else if (o instanceof Map<?, ?> item) {
                    Object idObj = item.get("id");
                    if (idObj == null) {
                        continue;
                    }
                    long parsedId = idObj instanceof Number n ? n.longValue() : Long.parseLong(idObj.toString());
                    ids.add(parsedId);
                    Object st = item.get("storage_type");
                    storageTypes.add(st != null ? st.toString() : "encrypted");
                }
            }
            if (ids.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "没有有效的卡密 ID"));
            }
            int deleted = cardService.deleteCards(ids, storageTypes);
            String msg = deleted == ids.size()
                    ? "已成功删除 " + deleted + " 条卡密"
                    : "已删除 " + deleted + " 条（部分 ID 不存在或无效）";
            return ResponseEntity.ok(Map.of("success", true, "message", msg, "deleted", deleted));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员批量解绑机器码。body.all=true 时对全库操作，否则需提供 ids。
     */
    @PostMapping("/admin/batch-unbind")
    public ResponseEntity<Map<String, Object>> batchUnbindCards(@RequestBody(required = false) Map<String, Object> body) {
        try {
            boolean allScope = body != null && Boolean.TRUE.equals(body.get("all"));
            List<Long> ids = new ArrayList<>();
            List<String> storageTypes = new ArrayList<>();
            if (!allScope) {
                parseBatchIdList(body, ids, storageTypes);
                if (ids.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "请勾选卡密或设置 all=true 解绑全库"));
                }
            }
            int unbound = cardService.adminBatchUnbind(ids, storageTypes, allScope);
            String msg = allScope
                    ? "已对全库解绑 " + unbound + " 条已绑定卡密"
                    : "已解绑 " + unbound + " 条卡密";
            return ResponseEntity.ok(Map.of("success", true, "message", msg, "unbound", unbound));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员批量加时/扣时。时间卡支持 hours/days，次数卡支持 times。
     */
    @PostMapping("/admin/batch-adjust")
    public ResponseEntity<Map<String, Object>> batchAdjustCards(@RequestBody(required = false) Map<String, Object> body) {
        try {
            if (body == null) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请提供请求体"));
            }
            String direction = body.get("adjust_direction") != null
                    ? body.get("adjust_direction").toString() : null;
            String unit = body.get("adjust_unit") != null ? body.get("adjust_unit").toString() : null;
            Object amountObj = body.get("adjust_amount");
            if (direction == null || unit == null || !(amountObj instanceof Number)) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "请提供 adjust_direction、adjust_unit、adjust_amount"));
            }
            int amount = ((Number) amountObj).intValue();
            boolean allScope = Boolean.TRUE.equals(body.get("all"));
            List<Long> ids = new ArrayList<>();
            List<String> storageTypes = new ArrayList<>();
            if (!allScope) {
                parseBatchIdList(body, ids, storageTypes);
                if (ids.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Map.of("success", false, "message", "请勾选卡密或设置 all=true 对全库操作"));
                }
            }
            Map<String, Integer> result = cardService.adminBatchAdjust(
                    ids, storageTypes, allScope, direction, unit, amount);
            int adjusted = result.getOrDefault("adjusted", 0);
            int skipped = result.getOrDefault("skipped", 0);
            String action = "subtract".equals(direction) ? "扣减" : "增加";
            String msg = allScope
                    ? String.format("全库已%s %d 条，跳过 %d 条", action, adjusted, skipped)
                    : String.format("已%s %d 条，跳过 %d 条", action, adjusted, skipped);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", msg,
                    "adjusted", adjusted,
                    "skipped", skipped
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private void parseBatchIdList(Map<String, Object> body, List<Long> ids, List<String> storageTypes) {
        if (body == null || !(body.get("ids") instanceof List<?> rawList)) {
            return;
        }
        for (Object o : rawList) {
            if (o instanceof Number num) {
                ids.add(num.longValue());
                storageTypes.add("encrypted");
            } else if (o instanceof String str && !str.isBlank()) {
                try {
                    ids.add(Long.parseLong(str.trim()));
                    storageTypes.add("encrypted");
                } catch (NumberFormatException ignored) {
                    // skip
                }
            } else if (o instanceof Map<?, ?> item) {
                Object idObj = item.get("id");
                if (idObj == null) {
                    continue;
                }
                long parsedId = idObj instanceof Number n ? n.longValue() : Long.parseLong(idObj.toString());
                ids.add(parsedId);
                Object st = item.get("storage_type");
                storageTypes.add(st != null ? st.toString() : "encrypted");
            }
        }
    }

    /**
     * 使用卡密
     */
    @RequestMapping(value = "/use", method = {RequestMethod.POST, RequestMethod.GET})
    public ResponseEntity<Map<String, Object>> useCard(
            @RequestParam(required = false) Map<String, String> requestParams,
            @RequestBody(required = false) Map<String, String> requestBody,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        Map<String, String> params = requestParams != null ? requestParams : new java.util.HashMap<>();
        if (requestBody != null) {
            params.putAll(requestBody);
        }

        String cardKey = params.get("card_key");
        String deviceId = params.get("device_id");
        String apiKeyStr = params.get("api_key");
        String machineCode = params.get("machine_code");

        String ipAddress = ClientIpUtils.resolve(httpRequest, params.get("ip_address"));

        if (cardKey == null || cardKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Card key is required"));
        }
        
        try {
            // Resolve API Key ID if provided
            Long apiKeyId = null;
            if (apiKeyStr != null && !apiKeyStr.isEmpty()) {
                ApiKey apiKey = apiKeyService.getByApiKey(apiKeyStr);
                if (apiKey == null) {
                    return ResponseEntity.status(403).body(Map.of("success", false, "message", "Invalid API Key"));
                }
                if (apiKey.getStatus() != 1) {
                    return ResponseEntity.status(403).body(Map.of("success", false, "message", "API Key is disabled"));
                }
                apiKeyId = apiKey.getId();
                
                // 检查是否开启了卡密加密验证
                if (Boolean.TRUE.equals(apiKey.getEnableCardEncryption())) {
                    try {
                        String decryptedKey = customCardObfuscator.deobfuscate(cardKey);
                        if (decryptedKey != null) {
                            cardKey = decryptedKey;
                        }
                    } catch (Exception ignored) {
                        // 保留原文，供简单卡密核销
                    }
                }
                
                // Update usage stats
                apiKeyService.updateUsage(apiKeyId);
            }
            
            cardService.useCard(cardKey, deviceId, ipAddress, apiKeyId, machineCode);
            return ResponseEntity.ok(Map.of("success", true, "message", "Card used successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 获取卡密使用趋势
     */
    @GetMapping("/trend")
    public ResponseEntity<Map<String, Object>> getUsageTrend(@RequestParam(defaultValue = "7") int days) {
        try {
            Map<String, Object> trend = cardService.getUsageTrend(days);
            return ResponseEntity.ok(trend);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 创建卡密请求对象
     */
    public static class CreateCardRequest {
        private int count;
        
        @JsonProperty("card_type")
        private String cardType;
        
        private int duration;

        @JsonProperty("duration_unit")
        private String durationUnit;
        
        @JsonProperty("total_count")
        private int totalCount;
        
        @JsonProperty("verify_method")
        private String verifyMethod;
        
        @JsonProperty("encryption_type")
        private String encryptionType;
        
        @JsonProperty("allow_reverify")
        private int allowReverify;

        @JsonProperty("api_key_id")
        private Long apiKeyId;

        /** 时间卡：同机器码上续期时是否将本卡时长叠加到未过期的原时间卡上 */
        @JsonProperty("stack_time_if_same_machine")
        private Boolean stackTimeIfSameMachine;

        /** 是否允许用户在首页自助解绑设备（机器码） */
        @JsonProperty("allow_self_unbind")
        private Boolean allowSelfUnbind;

        /** 自助解绑时须验证原设备码 */
        @JsonProperty("require_device_unbind")
        private Boolean requireDeviceUnbind;

        /** 自助解绑冷却间隔（小时），0=不限 */
        @JsonProperty("unbind_cooldown_hours")
        private Integer unbindCooldownHours;

        /** 自助解绑次数上限，0=不限 */
        @JsonProperty("unbind_max_count")
        private Integer unbindMaxCount;

        @JsonProperty("use_encrypted")
        private Boolean useEncrypted;

        @JsonProperty("key_length")
        private Integer keyLength;

        @JsonProperty("manual_card_keys")
        private List<String> manualCardKeys;

        /** 简单卡密自动递增前缀（可选，如 VIP → VIP0001） */
        @JsonProperty("key_prefix")
        private String keyPrefix;

        /** 逐条导入明细（含类型、时长/次数）；优先于 manual_card_keys */
        @JsonProperty("import_items")
        private List<ImportCardItem> importItems;

        // Getters and Setters
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }

        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }

        public String getDurationUnit() { return durationUnit; }
        public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }

        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

        public String getVerifyMethod() { return verifyMethod; }
        public void setVerifyMethod(String verifyMethod) { this.verifyMethod = verifyMethod; }

        public String getEncryptionType() { return encryptionType; }
        public void setEncryptionType(String encryptionType) { this.encryptionType = encryptionType; }

        public int getAllowReverify() { return allowReverify; }
        public void setAllowReverify(int allowReverify) { this.allowReverify = allowReverify; }

        public Long getApiKeyId() { return apiKeyId; }
        public void setApiKeyId(Long apiKeyId) { this.apiKeyId = apiKeyId; }

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

        public Boolean getUseEncrypted() { return useEncrypted; }
        public void setUseEncrypted(Boolean useEncrypted) { this.useEncrypted = useEncrypted; }

        public Integer getKeyLength() { return keyLength; }
        public void setKeyLength(Integer keyLength) { this.keyLength = keyLength; }

        public List<String> getManualCardKeys() { return manualCardKeys; }
        public void setManualCardKeys(List<String> manualCardKeys) { this.manualCardKeys = manualCardKeys; }

        public String getKeyPrefix() { return keyPrefix; }
        public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }

        public List<ImportCardItem> getImportItems() { return importItems; }
        public void setImportItems(List<ImportCardItem> importItems) { this.importItems = importItems; }
    }

    public static class ImportCardItem {
        @JsonProperty("card_key")
        private String cardKey;

        @JsonProperty("card_type")
        private String cardType;

        private int duration;

        @JsonProperty("duration_unit")
        private String durationUnit;

        @JsonProperty("total_count")
        private int totalCount;

        public String getCardKey() { return cardKey; }
        public void setCardKey(String cardKey) { this.cardKey = cardKey; }

        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }

        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }

        public String getDurationUnit() { return durationUnit; }
        public void setDurationUnit(String durationUnit) { this.durationUnit = durationUnit; }

        public int getTotalCount() { return totalCount; }
        public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    }
}
