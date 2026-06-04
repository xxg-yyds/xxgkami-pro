package org.xxg.backend.backend.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.entity.ApiKey;
import org.xxg.backend.backend.entity.Card;
import org.xxg.backend.backend.service.ApiKeyService;
import org.xxg.backend.backend.service.CardService;

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
    public ResponseEntity<Map<String, Object>> createCards(@RequestBody CreateCardRequest request) {
        try {
            // Default admin user for now (ID: 2, Username: admin)
            // In a real app, retrieve from SecurityContext
            Long adminId = 2L;
            String adminName = "admin";
            String creatorType = "admin";

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
                        request.getManualCardKeys()
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
                        Boolean.TRUE.equals(request.getAllowSelfUnbind())
                );
            }
            return ResponseEntity.ok(Map.of("success", true, "data", cards));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * 管理员获取所有卡密
     */
    @GetMapping("/admin/all")
    public ResponseEntity<Map<String, Object>> getAllCards() {
        try {
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
            @RequestBody Map<String, Object> body) {
        try {
            cardService.adminUpdateCard(id, body);
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

        String ipAddress = params.get("ip_address");
        if (ipAddress == null || ipAddress.isEmpty()) {
            ipAddress = httpRequest.getRemoteAddr();
        }

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

        @JsonProperty("use_encrypted")
        private Boolean useEncrypted;

        @JsonProperty("key_length")
        private Integer keyLength;

        @JsonProperty("manual_card_keys")
        private List<String> manualCardKeys;

        // Getters and Setters
        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }

        public String getCardType() { return cardType; }
        public void setCardType(String cardType) { this.cardType = cardType; }

        public int getDuration() { return duration; }
        public void setDuration(int duration) { this.duration = duration; }

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

        public Boolean getUseEncrypted() { return useEncrypted; }
        public void setUseEncrypted(Boolean useEncrypted) { this.useEncrypted = useEncrypted; }

        public Integer getKeyLength() { return keyLength; }
        public void setKeyLength(Integer keyLength) { this.keyLength = keyLength; }

        public List<String> getManualCardKeys() { return manualCardKeys; }
        public void setManualCardKeys(List<String> manualCardKeys) { this.manualCardKeys = manualCardKeys; }
    }
}
