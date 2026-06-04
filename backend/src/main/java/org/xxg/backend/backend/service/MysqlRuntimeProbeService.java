package org.xxg.backend.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 探测当前 JDBC 所连 MySQL 版本并推荐种子脚本系列（56 / 80）。
 */
@Service
public class MysqlRuntimeProbeService {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)");

    private final DataSource dataSource;

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/kami}")
    private String datasourceUrl;

    public MysqlRuntimeProbeService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> probe() {
        Map<String, Object> r = new HashMap<>();
        r.put("reachable", false);
        try (Connection conn = dataSource.getConnection()) {
            String version;
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                if (!rs.next()) {
                    r.put("error", "无法读取 VERSION()");
                    return r;
                }
                version = rs.getString(1);
            }
            String series = recommendSqlSeries(version);
            r.put("reachable", true);
            r.put("version", version);
            r.put("mariadb", version != null && version.toLowerCase().contains("mariadb"));
            r.put("recommendedSqlSeries", series);
            r.put("recommendedLabel", "80".equals(series) ? "MySQL 8.0+" : "MySQL 5.0+ / 5.6+ / MariaDB");
            r.put("database", resolveSchemaName());
        } catch (Exception e) {
            r.put("error", e.getMessage());
        }
        return r;
    }

    public String recommendSqlSeries(String version) {
        if (version == null || version.isBlank()) {
            return "56";
        }
        String lower = version.toLowerCase();
        if (lower.contains("mariadb")) {
            return "56";
        }
        Matcher m = VERSION_PATTERN.matcher(version);
        if (m.find()) {
            int major = Integer.parseInt(m.group(1));
            if (major >= 8) {
                return "80";
            }
        }
        return "56";
    }

    private String resolveSchemaName() {
        Matcher m = Pattern.compile("jdbc:mysql://[^/]+/([^?]+)").matcher(datasourceUrl);
        if (m.find()) {
            return m.group(1);
        }
        return "kami";
    }
}
