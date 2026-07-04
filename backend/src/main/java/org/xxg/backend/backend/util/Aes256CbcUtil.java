package org.xxg.backend.backend.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * AES-256-CBC 加解密（PKCS5Padding，密文 Base64）
 */
public final class Aes256CbcUtil {

    public static final String ALGORITHM = "AES-256-CBC";
    public static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    public static final String PADDING = "PKCS5Padding";
    public static final String ENCODING = "Base64";

    private Aes256CbcUtil() {
    }

    public static byte[] decodeKeyOrIv(String base64Value) {
        if (base64Value == null || base64Value.isBlank()) {
            throw new IllegalArgumentException("密钥或 IV 不能为空");
        }
        return Base64.getDecoder().decode(base64Value.trim());
    }

    public static String encryptToBase64(String plaintext, byte[] keyBytes, byte[] ivBytes) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new IllegalArgumentException("AES 加密失败: " + e.getMessage(), e);
        }
    }

    public static String decryptFromBase64(String ciphertextBase64, byte[] keyBytes, byte[] ivBytes) {
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(ivBytes);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            byte[] decoded = Base64.getDecoder().decode(ciphertextBase64.trim());
            byte[] plain = cipher.doFinal(decoded);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalArgumentException("AES 解密失败: " + e.getMessage(), e);
        }
    }

    public static void validateKeyAndIv(byte[] keyBytes, byte[] ivBytes) {
        if (keyBytes == null || keyBytes.length != 32) {
            throw new IllegalArgumentException("AES-256 密钥须为 32 字节（Base64 解码后）");
        }
        if (ivBytes == null || ivBytes.length != 16) {
            throw new IllegalArgumentException("CBC 偏移量 IV 须为 16 字节（Base64 解码后）");
        }
    }

    public static String generateRandomBase64(int byteLength) {
        byte[] bytes = new byte[byteLength];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
