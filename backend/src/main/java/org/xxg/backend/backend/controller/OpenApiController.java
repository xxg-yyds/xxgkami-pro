package org.xxg.backend.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.entity.ApiKey;
import org.xxg.backend.backend.entity.Card;
import org.xxg.backend.backend.entity.OpenPlatformToken;
import org.xxg.backend.backend.service.ApiKeyService;
import org.xxg.backend.backend.service.CardService;
import org.xxg.backend.backend.service.OpenPlatformTokenService;
import org.xxg.backend.backend.service.OpenApiParamEncryptionService;
import org.xxg.backend.backend.util.ClientIpUtils;
import org.xxg.backend.backend.util.CustomCardObfuscator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1")
@CrossOrigin
public class OpenApiController {

    private static final int MAX_OPEN_CREATE_COUNT = 100;

    private final ApiKeyService apiKeyService;
    private final CardService cardService;
    private final CustomCardObfuscator customCardObfuscator;
    private final OpenPlatformTokenService openPlatformTokenService;
    private final OpenApiParamEncryptionService openApiParamEncryptionService;

    public OpenApiController(
            ApiKeyService apiKeyService,
            CardService cardService,
            CustomCardObfuscator customCardObfuscator,
            OpenPlatformTokenService openPlatformTokenService,
            OpenApiParamEncryptionService openApiParamEncryptionService
    ) {
        this.apiKeyService = apiKeyService;
        this.cardService = cardService;
        this.customCardObfuscator = customCardObfuscator;
        this.openPlatformTokenService = openPlatformTokenService;
        this.openApiParamEncryptionService = openApiParamEncryptionService;
    }

    private static final class OpenApiAuth {
        final ResponseEntity<Map<String, Object>> error;
        final ApiKey apiKey;
        final String cardKey;

        private OpenApiAuth(ResponseEntity<Map<String, Object>> error, ApiKey apiKey, String cardKey) {
            this.error = error;
            this.apiKey = apiKey;
            this.cardKey = cardKey;
        }

        static OpenApiAuth failure(ResponseEntity<Map<String, Object>> error) {
            return new OpenApiAuth(error, null, null);
        }

        static OpenApiAuth success(ApiKey apiKey, String cardKey) {
            return new OpenApiAuth(null, apiKey, cardKey);
        }

        boolean ok() {
            return error == null;
        }
    }

    private ResponseEntity<Map<String, Object>> errorResponse(int httpStatus, int code, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", code);
        response.put("message", message);
        response.put("success", false);
        return ResponseEntity.status(httpStatus).body(response);
    }

    private ResponseEntity<Map<String, Object>> successResponse(String message, Map<String, Object> data) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 200);
        response.put("message", message);
        response.put("success", true);
        if (data != null) {
            response.put("data", data);
        }
        return ResponseEntity.ok(response);
    }

    private ApiKey authenticateApiKeyOnly(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalArgumentException("API Key is required");
        }
        ApiKey keyEntity = apiKeyService.getByApiKey(apiKey);
        if (keyEntity == null) {
            throw new IllegalArgumentException("Invalid API Key");
        }
        if (keyEntity.getStatus() != 1) {
            throw new IllegalArgumentException("API Key is disabled");
        }
        return keyEntity;
    }

    private String extractOpenToken(HttpServletRequest request, Map<String, Object> body) {
        if (body != null) {
            String fromBody = stringVal(body, "open_token");
            if (fromBody != null) {
                return fromBody;
            }
        }
        String xHeader = request.getHeader("X-Open-Token");
        if (xHeader != null && !xHeader.isBlank()) {
            return xHeader.trim();
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String param = request.getParameter("open_token");
        return param != null ? param.trim() : null;
    }

    private OpenPlatformToken requireOpenPlatformToken(HttpServletRequest request, Map<String, Object> body) {
        String raw = extractOpenToken(request, body);
        if (raw == null || raw.isEmpty()) {
            throw new IllegalArgumentException(
                    "开放平台 Token 为必填，请通过 Header Authorization: Bearer <token>、X-Open-Token 或参数 open_token 传递");
        }
        OpenPlatformToken token = openPlatformTokenService.validateAndTouch(raw);
        if (token == null) {
            throw new IllegalArgumentException("无效、已吊销或已过期的开放平台 Token");
        }
        return token;
    }

    private OpenApiAuth authenticate(String apiKey, String cardKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return OpenApiAuth.failure(errorResponse(401, 401, "API Key is required"));
        }
        if (cardKey == null || cardKey.isEmpty()) {
            return OpenApiAuth.failure(errorResponse(400, 400, "Card Key is required"));
        }

        ApiKey keyEntity = apiKeyService.getByApiKey(apiKey);
        if (keyEntity == null) {
            return OpenApiAuth.failure(errorResponse(403, 403, "Invalid API Key"));
        }
        if (keyEntity.getStatus() != 1) {
            return OpenApiAuth.failure(errorResponse(403, 403, "API Key is disabled"));
        }

        String resolvedCardKey = cardKey;
        if (Boolean.TRUE.equals(keyEntity.getEnableCardEncryption())) {
            try {
                String decryptedKey = customCardObfuscator.deobfuscate(cardKey);
                if (decryptedKey != null) {
                    resolvedCardKey = decryptedKey;
                }
            } catch (Exception ignored) {
                // 保留原文，供简单卡密核销
            }
        }
        return OpenApiAuth.success(keyEntity, resolvedCardKey);
    }

    private String resolveIpAddress(String ipAddress, HttpServletRequest request) {
        if (ipAddress == null || ipAddress.isEmpty()) {
            return ClientIpUtils.resolve(request, null);
        }
        return ClientIpUtils.resolve(request, ipAddress);
    }

    private ResponseEntity<Map<String, Object>> executeUseCard(
            String apiKey, String cardKey, String deviceId, String ipAddress, String machineCode,
            HttpServletRequest request, boolean includeActivationData
    ) {
        OpenApiAuth auth = authenticate(apiKey, cardKey);
        if (!auth.ok()) {
            return auth.error;
        }
        ipAddress = resolveIpAddress(ipAddress, request);

        Map<String, Object> response = new HashMap<>();
        try {
            Card card = cardService.useCard(
                    auth.cardKey, deviceId, ipAddress, auth.apiKey.getId(), machineCode);
            apiKeyService.updateUsage(auth.apiKey.getId());

            response.put("code", 200);
            response.put("message", includeActivationData ? "授权成功" : "Card used successfully");
            response.put("success", true);
            if (includeActivationData) {
                response.put("data", cardService.buildOpenApiActivationData(card));
            }
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 400);
            response.put("message", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }

    private ResponseEntity<Map<String, Object>> executeUnbindDevice(
            String apiKey, String cardKey, String machineCode
    ) {
        OpenApiAuth auth = authenticate(apiKey, cardKey);
        if (!auth.ok()) {
            return auth.error;
        }

        Map<String, Object> response = new HashMap<>();
        try {
            cardService.apiUnbindDevice(auth.cardKey, machineCode, auth.apiKey.getId());
            apiKeyService.updateUsage(auth.apiKey.getId());

            response.put("code", 200);
            response.put("message", "解绑成功");
            response.put("success", true);
            response.put("data", Map.of("unbound", true));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("code", 400);
            response.put("message", e.getMessage());
            response.put("success", false);
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping(value = "/use_card", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> useCardJson(
            HttpServletRequest request,
            @RequestBody Map<String, String> jsonBody
    ) {
        return executeUseCard(
                jsonBody.get("api_key"),
                jsonBody.get("card_key"),
                jsonBody.get("device_id"),
                jsonBody.get("ip_address"),
                jsonBody.get("machine_code"),
                request,
                false
        );
    }

    @RequestMapping(value = "/use_card")
    public ResponseEntity<Map<String, Object>> useCard(
            HttpServletRequest request,
            @RequestParam(value = "api_key", required = false) String apiKey,
            @RequestParam(value = "card_key", required = false) String cardKey,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "ip_address", required = false) String ipAddress,
            @RequestParam(value = "machine_code", required = false) String machineCode
    ) {
        return executeUseCard(apiKey, cardKey, deviceId, ipAddress, machineCode, request, false);
    }

    /** 开通授权：核销/激活卡密，并返回剩余时长或次数等状态 */
    @PostMapping(value = "/activate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> activateJson(
            HttpServletRequest request,
            @RequestBody Map<String, String> jsonBody
    ) {
        return executeUseCard(
                jsonBody.get("api_key"),
                jsonBody.get("card_key"),
                jsonBody.get("device_id"),
                jsonBody.get("ip_address"),
                jsonBody.get("machine_code"),
                request,
                true
        );
    }

    @RequestMapping(value = "/activate")
    public ResponseEntity<Map<String, Object>> activate(
            HttpServletRequest request,
            @RequestParam(value = "api_key", required = false) String apiKey,
            @RequestParam(value = "card_key", required = false) String cardKey,
            @RequestParam(value = "device_id", required = false) String deviceId,
            @RequestParam(value = "ip_address", required = false) String ipAddress,
            @RequestParam(value = "machine_code", required = false) String machineCode
    ) {
        return executeUseCard(apiKey, cardKey, deviceId, ipAddress, machineCode, request, true);
    }

    /** 解绑设备码：清空卡密绑定的 machine_code / device_id */
    @PostMapping(value = "/unbind_device", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> unbindDeviceJson(@RequestBody Map<String, String> jsonBody) {
        return executeUnbindDevice(
                jsonBody.get("api_key"),
                jsonBody.get("card_key"),
                jsonBody.get("machine_code")
        );
    }

    @RequestMapping(value = "/unbind_device")
    public ResponseEntity<Map<String, Object>> unbindDevice(
            @RequestParam(value = "api_key", required = false) String apiKey,
            @RequestParam(value = "card_key", required = false) String cardKey,
            @RequestParam(value = "machine_code", required = false) String machineCode
    ) {
        return executeUnbindDevice(apiKey, cardKey, machineCode);
    }

    /** 健康检查：无需鉴权，用于探测服务是否正常运行 */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("service", "xxgkami-open-api");
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("param_encryption", openApiParamEncryptionService.getPublicMeta());
        return successResponse("服务运行正常", data);
    }

    /** 连通性检测：可选传入 api_key 以同时验证密钥是否有效 */
    @RequestMapping("/ping")
    public ResponseEntity<Map<String, Object>> ping(
            @RequestParam(value = "api_key", required = false) String apiKey
    ) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "ok");
        data.put("timestamp", LocalDateTime.now().toString());
        data.put("param_encryption", openApiParamEncryptionService.getPublicMeta());
        if (apiKey != null && !apiKey.isEmpty()) {
            try {
                ApiKey keyEntity = authenticateApiKeyOnly(apiKey);
                apiKeyService.updateUsage(keyEntity.getId());
                data.put("api_key_valid", true);
                data.put("api_key_id", keyEntity.getId());
                data.put("key_name", keyEntity.getKeyName());
                data.put("enable_card_encryption", Boolean.TRUE.equals(keyEntity.getEnableCardEncryption()));
            } catch (IllegalArgumentException e) {
                return errorResponse(403, 403, e.getMessage());
            }
        }
        return successResponse("pong", data);
    }

    /** 验证 API Key 是否有效（须传入 api_key） */
    @RequestMapping("/verify_api_key")
    public ResponseEntity<Map<String, Object>> verifyApiKey(
            @RequestParam(value = "api_key", required = false) String apiKey
    ) {
        try {
            ApiKey keyEntity = authenticateApiKeyOnly(apiKey);
            apiKeyService.updateUsage(keyEntity.getId());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("valid", true);
            data.put("api_key_id", keyEntity.getId());
            data.put("key_name", keyEntity.getKeyName());
            data.put("status", keyEntity.getStatus());
            data.put("enable_card_encryption", Boolean.TRUE.equals(keyEntity.getEnableCardEncryption()));
            data.put("require_machine_code", Boolean.TRUE.equals(keyEntity.getRequireMachineCode()));
            return successResponse("API Key 有效", data);
        } catch (IllegalArgumentException e) {
            return errorResponse(403, 403, e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> executeCreateCards(HttpServletRequest request, Map<String, Object> body) {
        String apiKeyStr = stringVal(body, "api_key");
        try {
            requireOpenPlatformToken(request, body);
            ApiKey keyEntity = authenticateApiKeyOnly(apiKeyStr);
            boolean exclusive = boolVal(body, "exclusive", false);
            boolean useEncrypted = boolVal(body, "use_encrypted", false);

            String cardType = stringVal(body, "card_type");
            if (cardType == null || cardType.isEmpty()) {
                cardType = "time";
            }
            if (!"time".equals(cardType) && !"count".equals(cardType)) {
                return errorResponse(400, 400, "card_type 须为 time 或 count");
            }

            int count = intVal(body, "count", 1);
            if (count < 1 || count > MAX_OPEN_CREATE_COUNT) {
                return errorResponse(400, 400, "count 须在 1～" + MAX_OPEN_CREATE_COUNT + " 之间");
            }

            int duration = intVal(body, "duration", 30);
            int totalCount = intVal(body, "total_count", 100);
            String durationUnit = stringVal(body, "duration_unit");
            if (durationUnit == null || durationUnit.isEmpty()) {
                durationUnit = "days";
            }
            if ("time".equals(cardType) && !CardService.isPermanentUnit(durationUnit)) {
                CardService.validateDurationForUnit(duration, durationUnit);
            }

            String verifyMethod = stringVal(body, "verify_method");
            if (verifyMethod == null || verifyMethod.isEmpty()) {
                verifyMethod = "web";
            } else if (!"web".equals(verifyMethod) && !"post".equals(verifyMethod) && !"get".equals(verifyMethod)) {
                return errorResponse(400, 400, "verify_method 须为 web、post 或 get");
            }
            int allowReverify = intVal(body, "allow_reverify", 0);
            boolean stackTime = boolVal(body, "stack_time_if_same_machine", false);
            boolean allowSelfUnbind = boolVal(body, "allow_self_unbind", false);
            Boolean requireDeviceUnbind = body.containsKey("require_device_unbind")
                    ? boolVal(body, "require_device_unbind", false) : null;
            Integer unbindCooldown = body.containsKey("unbind_cooldown_hours")
                    ? intVal(body, "unbind_cooldown_hours", 0) : null;
            Integer unbindMax = body.containsKey("unbind_max_count")
                    ? intVal(body, "unbind_max_count", 0) : null;

            Long apiKeyId = exclusive ? keyEntity.getId() : null;
            String creatorType = "admin";
            Long creatorId = keyEntity.getId();
            String creatorName = (keyEntity.getKeyName() != null ? keyEntity.getKeyName() : "API") + " (open-api)";

            List<Card> cards;
            if (useEncrypted) {
                cards = cardService.createCards(
                        count, cardType, duration, totalCount, verifyMethod, "advanced",
                        allowReverify, creatorType, creatorId, creatorName, apiKeyId,
                        stackTime, allowSelfUnbind, durationUnit,
                        requireDeviceUnbind, unbindCooldown, unbindMax
                );
            } else {
                int keyLength = intVal(body, "key_length", 16);
                String keyPrefix = stringVal(body, "key_prefix");
                List<String> manualKeys = stringListVal(body, "manual_card_keys");
                if (manualKeys != null && !manualKeys.isEmpty() && manualKeys.size() != count) {
                    return errorResponse(400, 400, "manual_card_keys 条数须与 count 一致");
                }
                cards = cardService.createSimpleCards(
                        count, cardType, duration, totalCount, verifyMethod, allowReverify,
                        creatorType, creatorId, creatorName, apiKeyId,
                        stackTime, allowSelfUnbind, keyLength, manualKeys, keyPrefix, durationUnit,
                        requireDeviceUnbind, unbindCooldown, unbindMax
                );
            }

            apiKeyService.updateUsage(keyEntity.getId());

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("count", cards.size());
            data.put("exclusive", exclusive);
            data.put("use_encrypted", useEncrypted);
            data.put("api_key_id", apiKeyId);
            data.put("card_type", cardType);
            data.put("cards", sanitizeCreatedCards(cards, useEncrypted));

            return successResponse("成功生成 " + cards.size() + " 条卡密", data);
        } catch (IllegalArgumentException e) {
            return errorResponse(403, 403, e.getMessage());
        } catch (Exception e) {
            return errorResponse(400, 400, e.getMessage());
        }
    }

    /** 通过 API Key 生成卡密：exclusive=true 为专属卡密，false 为普通全局卡密 */
    @PostMapping(value = "/create_cards", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createCardsJson(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        return executeCreateCards(request, body != null ? body : Map.of());
    }

    @RequestMapping("/create_cards")
    public ResponseEntity<Map<String, Object>> createCards(
            HttpServletRequest request,
            @RequestParam(value = "open_token", required = false) String openToken,
            @RequestParam(value = "api_key", required = false) String apiKey,
            @RequestParam(value = "exclusive", required = false) Boolean exclusive,
            @RequestParam(value = "use_encrypted", required = false) Boolean useEncrypted,
            @RequestParam(value = "card_type", required = false) String cardType,
            @RequestParam(value = "count", required = false) Integer count,
            @RequestParam(value = "duration", required = false) Integer duration,
            @RequestParam(value = "duration_unit", required = false) String durationUnit,
            @RequestParam(value = "total_count", required = false) Integer totalCount,
            @RequestParam(value = "verify_method", required = false) String verifyMethod,
            @RequestParam(value = "allow_reverify", required = false) Integer allowReverify,
            @RequestParam(value = "stack_time_if_same_machine", required = false) Boolean stackTime,
            @RequestParam(value = "allow_self_unbind", required = false) Boolean allowSelfUnbind,
            @RequestParam(value = "key_length", required = false) Integer keyLength,
            @RequestParam(value = "key_prefix", required = false) String keyPrefix
    ) {
        Map<String, Object> body = new HashMap<>();
        if (openToken != null) body.put("open_token", openToken);
        if (apiKey != null) body.put("api_key", apiKey);
        if (exclusive != null) body.put("exclusive", exclusive);
        if (useEncrypted != null) body.put("use_encrypted", useEncrypted);
        if (cardType != null) body.put("card_type", cardType);
        if (count != null) body.put("count", count);
        if (duration != null) body.put("duration", duration);
        if (durationUnit != null) body.put("duration_unit", durationUnit);
        if (totalCount != null) body.put("total_count", totalCount);
        if (verifyMethod != null) body.put("verify_method", verifyMethod);
        if (allowReverify != null) body.put("allow_reverify", allowReverify);
        if (stackTime != null) body.put("stack_time_if_same_machine", stackTime);
        if (allowSelfUnbind != null) body.put("allow_self_unbind", allowSelfUnbind);
        if (keyLength != null) body.put("key_length", keyLength);
        if (keyPrefix != null) body.put("key_prefix", keyPrefix);
        return executeCreateCards(request, body);
    }

    /** 创建 API Key（须开放平台 Token，Header: Authorization: Bearer &lt;open_token&gt;） */
    @PostMapping(value = "/create_api_key", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createApiKeyJson(
            HttpServletRequest request,
            @RequestBody Map<String, Object> body
    ) {
        try {
            requireOpenPlatformToken(request, body != null ? body : Map.of());
            String name = stringVal(body, "name");
            if (name == null || name.isBlank()) {
                name = stringVal(body, "key_name");
            }
            if (name == null || name.isBlank()) {
                return errorResponse(400, 400, "name 为必填");
            }
            String description = stringVal(body, "description");
            boolean enableEncryption = boolVal(body, "enable_card_encryption", false);

            ApiKey created = apiKeyService.createApiKey(name.trim(), description, enableEncryption);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("id", created.getId());
            data.put("key_name", created.getKeyName());
            data.put("api_key", created.getApiKey());
            data.put("status", created.getStatus());
            data.put("enable_card_encryption", Boolean.TRUE.equals(created.getEnableCardEncryption()));
            data.put("create_time", created.getCreateTime() != null ? created.getCreateTime().toString() : null);

            return successResponse("API Key 创建成功", data);
        } catch (IllegalArgumentException e) {
            return errorResponse(403, 403, e.getMessage());
        } catch (RuntimeException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "创建失败";
            return errorResponse(400, 400, msg);
        }
    }

    private List<Map<String, Object>> sanitizeCreatedCards(List<Card> cards, boolean useEncrypted) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Card c : cards) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("card_key", c.getCardKey());
            row.put("card_type", c.getCardType());
            if ("time".equals(c.getCardType())) {
                row.put("duration", c.getDuration());
                row.put("duration_unit", c.getDurationUnit());
            } else {
                row.put("total_count", c.getTotalCount());
            }
            if (useEncrypted) {
                row.put("encryption_type", c.getEncryptionType());
            }
            out.add(row);
        }
        return out;
    }

    private static String stringVal(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key)) {
            return null;
        }
        Object v = m.get(key);
        if (v == null) {
            return null;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean boolVal(Map<String, Object> m, String key, boolean def) {
        if (m == null || !m.containsKey(key)) {
            return def;
        }
        Object v = m.get(key);
        if (v instanceof Boolean) {
            return (Boolean) v;
        }
        if (v instanceof Number) {
            return ((Number) v).intValue() != 0;
        }
        String s = String.valueOf(v).trim();
        return "true".equalsIgnoreCase(s) || "1".equals(s) || "yes".equalsIgnoreCase(s);
    }

    private static int intVal(Map<String, Object> m, String key, int def) {
        if (m == null || !m.containsKey(key)) {
            return def;
        }
        Object v = m.get(key);
        if (v instanceof Number) {
            return ((Number) v).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringListVal(Map<String, Object> m, String key) {
        if (m == null || !m.containsKey(key)) {
            return null;
        }
        Object v = m.get(key);
        if (!(v instanceof List)) {
            return null;
        }
        List<String> out = new ArrayList<>();
        for (Object item : (List<?>) v) {
            if (item != null) {
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out.isEmpty() ? null : out;
    }
}
