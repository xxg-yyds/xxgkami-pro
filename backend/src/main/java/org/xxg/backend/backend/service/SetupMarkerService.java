package org.xxg.backend.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 读写首次安装完成标记文件。
 */
@Service
public class SetupMarkerService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern JDBC_DB_PATTERN = Pattern.compile("jdbc:mysql://[^/]+/([^?]+)");

    private final DataSource dataSource;

    @Value("${xxgkami.setup.marker-file:data/.xxgkami-setup.complete}")
    private String markerFilePath;

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/kami}")
    private String datasourceUrl;

    public SetupMarkerService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean isSetupComplete() {
        return Files.isRegularFile(resolveMarkerPath());
    }

    /** 标记存在且核心表 admins 已导入后才访问业务库。 */
    public boolean isBusinessDatabaseReady() {
        if (!isSetupComplete()) {
            return false;
        }
        return hasAdminsTable();
    }

    public Map<String, Object> skippedDatabaseProbe() {
        Map<String, Object> m = new HashMap<>();
        m.put("reachable", false);
        m.put("skipped", true);
        m.put("reason", isSetupComplete() ? "schema_incomplete" : "setup_pending");
        m.put("message", isSetupComplete()
                ? "检测到安装标记但业务表未就绪，请重新执行安装向导"
                : "请先完成安装向导");
        return m;
    }

    public Path resolveMarkerPath() {
        return Path.of(markerFilePath).toAbsolutePath().normalize();
    }

    public void writeComplete(Map<String, Object> meta) throws IOException {
        Path path = resolveMarkerPath();
        Files.createDirectories(path.getParent());
        Map<String, Object> body = new HashMap<>(meta != null ? meta : Map.of());
        body.putIfAbsent("completedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body));
    }

    public Map<String, Object> readMeta() {
        Path path = resolveMarkerPath();
        if (!Files.isRegularFile(path)) {
            return Map.of();
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = MAPPER.readValue(Files.readString(path), Map.class);
            return m;
        } catch (Exception e) {
            return Map.of("raw", true);
        }
    }

    private boolean hasAdminsTable() {
        String schema = resolveSchemaName();
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='"
                             + schema.replace("'", "''") + "' AND table_name='admins'")) {
            return rs.next() && rs.getInt(1) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveSchemaName() {
        Matcher m = JDBC_DB_PATTERN.matcher(datasourceUrl);
        if (m.find()) {
            return m.group(1);
        }
        return "kami";
    }
}
