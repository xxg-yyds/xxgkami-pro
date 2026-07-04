package org.xxg.backend.backend.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class OpenPlatformTokenHasher {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String PREFIX = "xxg_";

    private OpenPlatformTokenHasher() {
    }

    /** 生成明文 Token（仅创建时返回一次） */
    public static String generateRawToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(PREFIX);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String hashToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Token 不能为空");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(rawToken.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /** 列表展示用前缀（不含完整密钥） */
    public static String displayPrefix(String rawToken) {
        if (rawToken == null || rawToken.length() <= 12) {
            return rawToken;
        }
        return rawToken.substring(0, 12) + "****";
    }
}
