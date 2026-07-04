package org.xxg.backend.backend.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 从反向代理头或直连地址解析客户端真实 IP。
 */
public final class ClientIpUtils {

    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "CF-Connecting-IP",
            "True-Client-IP",
            "X-Client-IP"
    };

    private ClientIpUtils() {
    }

    public static String resolve(HttpServletRequest request) {
        return resolve(request, null);
    }

    /**
     * @param explicitIp 客户端/API 显式传入的 ip_address；为空时从请求头解析
     */
    public static String resolve(HttpServletRequest request, String explicitIp) {
        if (explicitIp != null && !explicitIp.isBlank() && !isUnknown(explicitIp)) {
            return firstIp(explicitIp);
        }
        if (request == null) {
            return null;
        }
        for (String header : IP_HEADERS) {
            String value = request.getHeader(header);
            if (value != null && !value.isBlank() && !isUnknown(value)) {
                return firstIp(value);
            }
        }
        return request.getRemoteAddr();
    }

    private static String firstIp(String raw) {
        if (raw == null) {
            return null;
        }
        String ip = raw.split(",")[0].trim();
        return ip.isEmpty() ? null : ip;
    }

    private static boolean isUnknown(String value) {
        return "unknown".equalsIgnoreCase(value.trim());
    }
}
