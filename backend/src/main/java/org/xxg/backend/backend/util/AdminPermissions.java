package org.xxg.backend.backend.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理员功能权限码（与前端菜单对应）
 */
public final class AdminPermissions {

    public static final String OVERVIEW = "overview";
    public static final String KEYS = "keys";
    public static final String PRICING = "pricing";
    public static final String ORDERS = "orders";
    public static final String API = "api";
    public static final String USERS = "users";
    public static final String NOTIFICATION = "notification";
    public static final String SETTINGS = "settings";
    public static final String MAINTENANCE = "maintenance";
    public static final String SYSTEM_INFO = "system_info";
    public static final String ADMINS = "admins";
    public static final String LOGS = "logs";

    public static final List<String> ALL = List.of(
            OVERVIEW, KEYS, PRICING, ORDERS, API, USERS, NOTIFICATION,
            SETTINGS, MAINTENANCE, SYSTEM_INFO, ADMINS, LOGS
    );

    private AdminPermissions() {
    }

    public static Set<String> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> set = new LinkedHashSet<>();
        for (String part : raw.split("[,;\\s]+")) {
            String p = part.trim();
            if (!p.isEmpty()) {
                set.add(p);
            }
        }
        return set;
    }

    public static String join(Set<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return "";
        }
        return String.join(",", permissions);
    }

    public static boolean has(Set<String> permissions, boolean isSuper, String code) {
        if (isSuper) {
            return true;
        }
        if (permissions == null || permissions.isEmpty()) {
            return false;
        }
        return permissions.contains(code);
    }

    public static Set<String> sanitize(Set<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> allowed = new LinkedHashSet<>(ALL);
        Set<String> result = new LinkedHashSet<>();
        for (String code : requested) {
            if (code != null && allowed.contains(code.trim())) {
                result.add(code.trim());
            }
        }
        return result;
    }

    public static Set<String> allSet() {
        return new LinkedHashSet<>(ALL);
    }
}
