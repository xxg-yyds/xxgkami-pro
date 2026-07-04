package org.xxg.backend.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.xxg.backend.backend.util.Aes256CbcUtil;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class OpenApiParamEncryptionService {

    public static final String SETTING_REQUEST_ENABLED = "openApiParamEncryptionEnabled";
    public static final String SETTING_RESPONSE_ENABLED = "openApiResponseEncryptionEnabled";
    public static final String SETTING_KEY = "openApiParamEncryptionKey";
    public static final String SETTING_IV = "openApiParamEncryptionIv";
    public static final String SETTING_PADDING = "openApiParamEncryptionPadding";
    public static final String SETTING_ENCODING = "openApiParamEncryptionEncoding";
    public static final String PAYLOAD_FIELD = "encrypted_payload";
    public static final String RESPONSE_ENCRYPTED_FLAG = "response_encrypted";

    private final SettingsService settingsService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenApiParamEncryptionService(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /** @deprecated 使用 {@link #isRequestEncryptionEnabled()} */
    public boolean isEnabled() {
        return isRequestEncryptionEnabled();
    }

    public boolean isRequestEncryptionEnabled() {
        return "true".equalsIgnoreCase(settingsService.getSetting(SETTING_REQUEST_ENABLED));
    }

    public boolean isResponseEncryptionEnabled() {
        return "true".equalsIgnoreCase(settingsService.getSetting(SETTING_RESPONSE_ENABLED));
    }

    public Map<String, Object> getAdminConfig() {
        ensureCryptoMaterial();
        Map<String, Object> config = buildCryptoMeta();
        config.put("request_enabled", isRequestEncryptionEnabled());
        config.put("response_enabled", isResponseEncryptionEnabled());
        config.put("enabled", isRequestEncryptionEnabled());
        config.put("key", settingsService.getSetting(SETTING_KEY));
        config.put("iv", settingsService.getSetting(SETTING_IV));
        return config;
    }

    public Map<String, Object> getPublicMeta() {
        Map<String, Object> meta = buildCryptoMeta();
        meta.put("request_enabled", isRequestEncryptionEnabled());
        meta.put("response_enabled", isResponseEncryptionEnabled());
        meta.put("enabled", isRequestEncryptionEnabled());
        if (isRequestEncryptionEnabled() || isResponseEncryptionEnabled()) {
            meta.put("key", settingsService.getSetting(SETTING_KEY));
            meta.put("iv", settingsService.getSetting(SETTING_IV));
        }
        return meta;
    }

    @Transactional
    public Map<String, Object> saveAdminConfig(Boolean requestEnabled, Boolean responseEnabled, boolean regenerateKey) {
        ensureCryptoMaterial();
        if (regenerateKey) {
            regenerateCryptoMaterial();
        }

        Map<String, String> updates = new LinkedHashMap<>();
        if (requestEnabled != null) {
            updates.put(SETTING_REQUEST_ENABLED, requestEnabled ? "true" : "false");
        }
        if (responseEnabled != null) {
            updates.put(SETTING_RESPONSE_ENABLED, responseEnabled ? "true" : "false");
        }
        updates.put(SETTING_PADDING, readPadding());
        updates.put(SETTING_ENCODING, readEncoding());
        settingsService.saveSettings(updates);
        return getAdminConfig();
    }

    public Map<String, String> decryptPayloadToParams(String encryptedPayload) {
        if (encryptedPayload == null || encryptedPayload.isBlank()) {
            throw new IllegalArgumentException("缺少 encrypted_payload");
        }
        String plaintext = decryptPlaintext(encryptedPayload);
        try {
            Map<String, String> params = objectMapper.readValue(plaintext, new TypeReference<>() {});
            Map<String, String> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    normalized.put(entry.getKey(), entry.getValue());
                }
            }
            if (normalized.isEmpty()) {
                throw new IllegalArgumentException("解密后的参数不能为空 JSON 对象");
            }
            return normalized;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("解密后的内容须为 JSON 对象，例如 {\"api_key\":\"...\",\"card_key\":\"...\"}", e);
        }
    }

    public String encryptParamsToPayload(Map<String, ?> params) {
        try {
            return encryptPlaintext(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            throw new IllegalArgumentException("参数加密失败: " + e.getMessage(), e);
        }
    }

    public String encryptPlaintext(String plaintext) {
        ensureCryptoMaterial();
        byte[] keyBytes = Aes256CbcUtil.decodeKeyOrIv(settingsService.getSetting(SETTING_KEY));
        byte[] ivBytes = Aes256CbcUtil.decodeKeyOrIv(settingsService.getSetting(SETTING_IV));
        Aes256CbcUtil.validateKeyAndIv(keyBytes, ivBytes);
        return Aes256CbcUtil.encryptToBase64(plaintext, keyBytes, ivBytes);
    }

    public String decryptPlaintext(String encryptedPayload) {
        ensureCryptoMaterial();
        byte[] keyBytes = Aes256CbcUtil.decodeKeyOrIv(settingsService.getSetting(SETTING_KEY));
        byte[] ivBytes = Aes256CbcUtil.decodeKeyOrIv(settingsService.getSetting(SETTING_IV));
        Aes256CbcUtil.validateKeyAndIv(keyBytes, ivBytes);
        return Aes256CbcUtil.decryptFromBase64(encryptedPayload, keyBytes, ivBytes);
    }

    public Map<String, Object> wrapEncryptedResponse(String jsonBody) {
        Map<String, Object> wrapped = new LinkedHashMap<>();
        wrapped.put(RESPONSE_ENCRYPTED_FLAG, true);
        wrapped.put(PAYLOAD_FIELD, encryptPlaintext(jsonBody));
        return wrapped;
    }

    private Map<String, Object> buildCryptoMeta() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("algorithm", Aes256CbcUtil.ALGORITHM);
        meta.put("padding", readPadding());
        meta.put("encoding", readEncoding());
        meta.put("payload_field", PAYLOAD_FIELD);
        meta.put("response_encrypted_flag", RESPONSE_ENCRYPTED_FLAG);
        return meta;
    }

    private void ensureCryptoMaterial() {
        String key = settingsService.getSetting(SETTING_KEY);
        String iv = settingsService.getSetting(SETTING_IV);
        if (key == null || key.isBlank() || iv == null || iv.isBlank()) {
            regenerateCryptoMaterial();
            return;
        }
        if (settingsService.getSetting(SETTING_PADDING) == null) {
            settingsService.saveSettings(Map.of(SETTING_PADDING, Aes256CbcUtil.PADDING));
        }
        if (settingsService.getSetting(SETTING_ENCODING) == null) {
            settingsService.saveSettings(Map.of(SETTING_ENCODING, Aes256CbcUtil.ENCODING));
        }
    }

    private void regenerateCryptoMaterial() {
        settingsService.saveSettings(Map.of(
                SETTING_KEY, Aes256CbcUtil.generateRandomBase64(32),
                SETTING_IV, Aes256CbcUtil.generateRandomBase64(16),
                SETTING_PADDING, Aes256CbcUtil.PADDING,
                SETTING_ENCODING, Aes256CbcUtil.ENCODING
        ));
    }

    private String readPadding() {
        String padding = settingsService.getSetting(SETTING_PADDING);
        return padding != null && !padding.isBlank() ? padding : Aes256CbcUtil.PADDING;
    }

    private String readEncoding() {
        String encoding = settingsService.getSetting(SETTING_ENCODING);
        return encoding != null && !encoding.isBlank() ? encoding : Aes256CbcUtil.ENCODING;
    }
}
