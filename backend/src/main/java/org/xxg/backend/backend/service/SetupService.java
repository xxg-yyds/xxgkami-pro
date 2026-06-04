package org.xxg.backend.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 首次安装向导：环境检测、MySQL 导入（覆盖/增量）、标记完成。
 */
@Service
public class SetupService {

    private static final String DB_NAME = "kami";
    private static final String DEFAULT_ADMIN_USER = "admin";
    private static final String DEFAULT_ADMIN_PASS = "123456";

    /** 智能更新检测/执行时忽略（运行时会话表，非业务结构） */
    private static final Set<String> MERGE_IGNORE_TABLES = Set.of(
            "spring_session",
            "spring_session_attributes"
    );

    @Autowired
    private SetupMarkerService setupMarkerService;

    @Autowired
    private SetupVersionService setupVersionService;

    @Autowired
    private SqlTranslateService sqlTranslateService;

    @Autowired
    private SeedSqlLocator seedSqlLocator;

    @Autowired
    private MysqlRuntimeProbeService mysqlRuntimeProbeService;

    @Autowired
    private RemoteUpdateService remoteUpdateService;

    @Autowired(required = false)
    private SystemMonitorService systemMonitorService;

    @Value("${spring.datasource.url:jdbc:mysql://localhost:3306/kami}")
    private String configuredDatasourceUrl;

    @Value("${spring.datasource.username:root}")
    private String configuredDatasourceUser;

    @Value("${spring.datasource.password:}")
    private String configuredDatasourcePassword;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        boolean markerComplete = setupMarkerService.isSetupComplete();
        boolean businessReady = setupMarkerService.isBusinessDatabaseReady();
        String localVersion = setupVersionService.readLocalAppVersion().orElse("0.0.0");
        String remoteVersion = businessReady
                ? fetchRemoteAppVersion().orElse(localVersion)
                : localVersion;
        String recordedVersion = null;
        try {
            if (setupVersionService.hasRecordedVersion()) {
                recordedVersion = setupVersionService.readRecordedVersion();
            }
        } catch (IOException ignored) {
        }

        boolean needsSetupWizard = !businessReady;
        boolean needsVersionUpgradeCheck = false;
        if (businessReady) {
            if (recordedVersion == null) {
                needsVersionUpgradeCheck = setupVersionService.compareVersions(localVersion, remoteVersion) >= 0;
            } else {
                needsVersionUpgradeCheck = setupVersionService.compareVersions(recordedVersion, localVersion) < 0;
            }
        }

        status.put("setupComplete", businessReady);
        status.put("setupMarkerPresent", markerComplete);
        status.put("needsSetupWizard", needsSetupWizard);
        status.put("needsVersionUpgradeCheck", needsVersionUpgradeCheck);
        status.put("localVersion", localVersion);
        status.put("remoteVersion", remoteVersion);
        status.put("recordedVersion", recordedVersion);
        status.put("versionFile", setupVersionService.resolvePath().toString());
        status.put("markerFile", setupMarkerService.resolveMarkerPath().toString());
        if (markerComplete) {
            status.put("markerMeta", setupMarkerService.readMeta());
        }
        if (businessReady) {
            try {
                Map<String, Object> db = probeConfiguredDatabase();
                status.put("configuredDatabase", db);
            } catch (Exception e) {
                status.put("configuredDatabase", Map.of("reachable", false, "error", e.getMessage()));
            }
        } else {
            Map<String, Object> db = new HashMap<>(setupMarkerService.skippedDatabaseProbe());
            db.putAll(mysqlRuntimeProbeService.probe());
            status.put("configuredDatabase", db);
        }
        return status;
    }

    public Map<String, Object> getEnvironment() {
        Map<String, Object> env = new HashMap<>();
        env.put("javaVersion", System.getProperty("java.version"));
        env.put("javaVendor", System.getProperty("java.vendor"));
        env.put("osName", System.getProperty("os.name"));
        env.put("osVersion", System.getProperty("os.version"));
        env.put("osArch", System.getProperty("os.arch"));
        env.put("userDir", System.getProperty("user.dir"));
        env.put("mysqlCliAvailable", commandExists("mysql"));
        env.put("mysqldumpCliAvailable", commandExists("mysqldump"));
        env.put("redis", probeRedis());
        Map<String, Object> sqlFiles = new HashMap<>();
        String kamiPath = null;
        try {
            kamiPath = sqlTranslateService.resolveMysql80Seed().map(Path::toString).orElse(null);
        } catch (IOException ignored) {
        }
        sqlFiles.put("mysql80", kamiPath);
        sqlFiles.put("mysql56Source", kamiPath);
        sqlFiles.put("mysql56AutoTranslate", true);
        sqlFiles.put("seedBundledInJar", seedSqlLocator.isBundledInJar());
        try {
            sqlFiles.put("mysql56", sqlTranslateService.getStatus().get("cacheFile"));
        } catch (Exception ignored) {
            sqlFiles.put("mysql56", null);
        }
        try {
            sqlFiles.put("simpleCards", seedSqlLocator.resolveSimpleCardsSql().map(Path::toString).orElse(null));
        } catch (IOException ignored) {
            sqlFiles.put("simpleCards", null);
        }
        env.put("sqlFiles", sqlFiles);
        if (systemMonitorService != null) {
            try {
                env.put("system", systemMonitorService.getSystemStatus());
            } catch (Exception ignored) {
            }
        }
        if (setupMarkerService.isBusinessDatabaseReady()) {
            try {
                env.put("configuredDatabase", probeConfiguredDatabase());
            } catch (Exception e) {
                env.put("configuredDatabase", Map.of("reachable", false, "error", e.getMessage()));
            }
        } else {
            Map<String, Object> db = new HashMap<>(setupMarkerService.skippedDatabaseProbe());
            db.putAll(mysqlRuntimeProbeService.probe());
            env.put("configuredDatabase", db);
        }
        return env;
    }

    public Map<String, Object> testMysqlConnection(String host, int port, String username, String password) {
        try (Connection conn = openServerConnection(host, port, username, password)) {
            String version;
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                rs.next();
                version = rs.getString(1);
            }
            Map<String, Object> r = new HashMap<>();
            r.put("ok", true);
            r.put("version", version);
            r.put("mariadb", version != null && version.toLowerCase().contains("mariadb"));
            r.put("recommendedSqlSeries", mysqlRuntimeProbeService.recommendSqlSeries(version));
            return r;
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    public Map<String, Object> checkKamiDatabase(String host, int port, String username, String password) {
        try (Connection conn = openServerConnection(host, port, username, password)) {
            boolean exists = schemaExists(conn, DB_NAME);
            int tableCount = exists ? countTables(conn, DB_NAME) : 0;
            boolean hasAdminsTable = exists && tableExistsInSchema(conn, DB_NAME, "admins");
            boolean freshInstall = !exists || tableCount == 0 || !hasAdminsTable;
            boolean needsStrategy = exists && tableCount > 0 && hasAdminsTable;
            Map<String, Object> r = new HashMap<>();
            r.put("ok", true);
            r.put("exists", exists);
            r.put("tableCount", tableCount);
            r.put("hasAdminsTable", hasAdminsTable);
            r.put("freshInstall", freshInstall);
            r.put("needsStrategy", needsStrategy);
            r.put("hasData", needsStrategy);
            return r;
        } catch (Exception e) {
            return Map.of("ok", false, "message", e.getMessage());
        }
    }

    public Map<String, Object> installDatabase(Map<String, Object> req) throws Exception {
        String host = str(req.get("host"), "localhost");
        int port = intVal(req.get("port"), 3306);
        String username = str(req.get("username"), "root");
        String password = str(req.get("password"), "");
        String sqlSeries = str(req.get("sqlSeries"), "80");
        String action = str(req.get("action"), "fresh");
        String confirmDelete = str(req.get("confirmDelete"), "");

        Path seedSql = resolveSeedSqlPath(sqlSeries);
        Path simpleSql = resolveSimpleCardsSql().orElse(null);

        try (Connection serverConn = openServerConnection(host, port, username, password)) {
            boolean exists = schemaExists(serverConn, DB_NAME);
            int tableCount = exists ? countTables(serverConn, DB_NAME) : 0;

            if ("overwrite".equals(action)) {
                if (!"DELETE".equals(confirmDelete)) {
                    throw new IllegalArgumentException("覆盖安装需确认码 DELETE");
                }
                if (exists && tableCount > 0) {
                    backupDatabase(host, port, username, password);
                }
                dropAndCreateDatabase(serverConn);
                importSqlFile(host, port, username, password, DB_NAME, seedSql);
            } else if ("merge".equals(action)) {
                boolean applyMerge = !Boolean.FALSE.equals(req.get("applyMerge"));
                if (applyMerge) {
                    mergeDatabase(host, port, username, password, seedSql);
                    if (simpleSql != null && !tableExistsInKami(host, port, username, password, "simple_cards")) {
                        importSqlFile(host, port, username, password, DB_NAME, simpleSql);
                    }
                }
            } else {
                boolean hasAdminsTable = exists && tableExistsInSchema(serverConn, DB_NAME, "admins");
                if (!exists || tableCount == 0) {
                    createDatabaseIfAbsent(serverConn);
                    importSqlFile(host, port, username, password, DB_NAME, seedSql);
                } else if (!hasAdminsTable) {
                    dropAndCreateDatabase(serverConn);
                    importSqlFile(host, port, username, password, DB_NAME, seedSql);
                } else {
                    throw new IllegalStateException("数据库已存在业务表，请选择覆盖或智能更新");
                }
            }
        }

        if (simpleSql != null && !"merge".equals(action)) {
            importSqlFile(host, port, username, password, DB_NAME, simpleSql);
        }

        updateDatasourceConfig(host, port, username, password);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("database", DB_NAME);
        result.put("sqlFile", seedSql.toString());
        result.put("action", action);
        applyAdminInfoFromDatabase(result);
        if ("merge".equals(action) && Boolean.FALSE.equals(req.get("applyMerge"))) {
            result.put("skippedMerge", true);
            result.put("message", "已跳过数据库更新，可直接进入系统");
        } else {
            result.put("message", "数据库安装完成，请使用下方管理员账号登录");
        }
        return result;
    }

    /**
     * 智能更新：对比种子库与当前 kami 库差异（不写入业务库）。
     */
    public Map<String, Object> analyzeMergeDatabase(Map<String, Object> req) throws Exception {
        String host = str(req.get("host"), "localhost");
        int port = intVal(req.get("port"), 3306);
        String username = str(req.get("username"), "root");
        String password = str(req.get("password"), "");
        String sqlSeries = str(req.get("sqlSeries"), "80");

        Path seedSql = resolveSeedSqlPath(sqlSeries);
        Path simpleSql = resolveSimpleCardsSql().orElse(null);

        boolean cliReady = commandExists("mysql") && commandExists("mysqldump");
        List<Map<String, Object>> items = new ArrayList<>();
        String tempDb = null;

        try (Connection serverConn = openServerConnection(host, port, username, password)) {
            createDatabaseIfAbsent(serverConn);
            tempDb = "kami_analyze_" + System.currentTimeMillis();
            try (Statement st = serverConn.createStatement()) {
                st.executeUpdate("CREATE DATABASE `" + tempDb + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            }
            importSqlFile(host, port, username, password, tempDb, seedSql);

            List<String> seedTables = listTables(serverConn, tempDb);
            for (String table : seedTables) {
                if (isMergeIgnoredTable(table)) {
                    continue;
                }
                Map<String, Object> item = new HashMap<>();
                item.put("table", table);
                long seedRows = countTableRows(serverConn, tempDb, table);

                if (!tableExistsInSchema(serverConn, DB_NAME, table)) {
                    item.put("changeType", "new_table");
                    item.put("seedRows", seedRows);
                    item.put("targetRows", 0L);
                    item.put("estimatedInsertRows", seedRows);
                    item.put("description", "新建表并导入 " + seedRows + " 行");
                    items.add(item);
                    continue;
                }

                long targetRows = countTableRows(serverConn, DB_NAME, table);
                Long missing = estimateMissingRows(serverConn, tempDb, DB_NAME, table);
                item.put("changeType", "merge_rows");
                item.put("seedRows", seedRows);
                item.put("targetRows", targetRows);
                item.put("estimatedInsertRows", missing);
                if (missing != null && missing == 0) {
                    item.put("description", "表已存在，未发现可补缺行");
                    continue;
                }
                if (missing != null) {
                    item.put("description", "表已存在，预计可插入约 " + missing + " 行（insert-ignore）");
                } else {
                    item.put("description", "表已存在，将尝试 insert-ignore 补缺行");
                }
                items.add(item);
            }

            if (simpleSql != null && !tableExistsInSchema(serverConn, DB_NAME, "simple_cards")) {
                Map<String, Object> sc = new HashMap<>();
                sc.put("table", "simple_cards");
                sc.put("changeType", "new_table");
                sc.put("seedRows", 0L);
                sc.put("targetRows", 0L);
                sc.put("estimatedInsertRows", 0L);
                sc.put("description", "将创建简单卡密表 simple_cards");
                items.add(sc);
            }
        } finally {
            if (tempDb != null) {
                try (Connection serverConn = openServerConnection(host, port, username, password);
                     Statement st = serverConn.createStatement()) {
                    st.executeUpdate("DROP DATABASE IF EXISTS `" + tempDb + "`");
                } catch (Exception ignored) {
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("hasChanges", !items.isEmpty());
        result.put("items", items);
        result.put("newTableCount", items.stream().filter(i -> "new_table".equals(i.get("changeType"))).count());
        result.put("mergeTableCount", items.stream().filter(i -> "merge_rows".equals(i.get("changeType"))).count());
        result.put("cliReady", cliReady);
        result.put("sqlFile", seedSql.toString());
        if (!cliReady) {
            result.put("warning", "未检测到 mysql/mysqldump 客户端，执行插入时可能失败");
        }
        if (items.isEmpty()) {
            result.put("summary", "当前数据库结构已与种子脚本一致，无需插入");
        } else {
            result.put("summary", "检测到 " + items.size() + " 项可更新内容");
        }
        return result;
    }

    private boolean tableExistsInKami(String host, int port, String user, String pass, String table) throws Exception {
        try (Connection conn = openServerConnection(host, port, user, pass)) {
            return tableExistsInSchema(conn, DB_NAME, table);
        }
    }

    private long countTableRows(Connection conn, String schema, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + schema + "`.`" + table + "`")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Long estimateMissingRows(Connection conn, String tempDb, String targetDb, String table) {
        try {
            if (!columnExists(conn, tempDb, table, "id")) {
                return null;
            }
            String sql = "SELECT COUNT(*) FROM `" + tempDb + "`.`" + table + "` t " +
                    "WHERE NOT EXISTS (SELECT 1 FROM `" + targetDb + "`.`" + table + "` k WHERE k.`id` = t.`id`)";
            try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private boolean columnExists(Connection conn, String schema, String table, String column) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA='" + schema +
                             "' AND TABLE_NAME='" + table + "' AND COLUMN_NAME='" + column + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    /**
     * 已安装系统：使用 application.properties 中的 JDBC 检测更新差异。
     */
    public Map<String, Object> analyzeMergeUsingConfigured() throws Exception {
        return analyzeMergeDatabase(buildMergeRequestFromConfigured());
    }

    /**
     * 已安装系统：按配置库连接执行或跳过智能更新。
     */
    public Map<String, Object> installUsingConfigured(Map<String, Object> req) throws Exception {
        boolean applyMerge = req != null && !Boolean.FALSE.equals(req.get("applyMerge"));
        Map<String, Object> conn = buildMergeRequestFromConfigured();
        if (applyMerge) {
            backupDatabase(
                    (String) conn.get("host"),
                    (int) conn.get("port"),
                    (String) conn.get("username"),
                    (String) conn.get("password")
            );
        }
        Map<String, Object> payload = new HashMap<>(conn);
        payload.put("action", "merge");
        payload.put("applyMerge", applyMerge);
        Map<String, Object> result = installDatabase(payload);
        applyAdminInfoFromDatabase(result);
        return result;
    }

    public Map<String, Object> completeSetup(Map<String, Object> meta) throws IOException {
        setupMarkerService.writeComplete(meta);
        writeCurrentAppVersionRecord();
        Map<String, Object> r = new HashMap<>();
        r.put("setupComplete", true);
        r.put("markerFile", setupMarkerService.resolveMarkerPath().toString());
        r.put("versionFile", setupVersionService.resolvePath().toString());
        applyAdminInfoFromDatabase(r);
        return r;
    }

    /**
     * 新版升级检测流程结束：写入当前版本记录（不重复写安装向导标记）。
     */
    public Map<String, Object> completeVersionUpgrade(Map<String, Object> meta) throws IOException {
        writeCurrentAppVersionRecord();
        Map<String, Object> r = new HashMap<>();
        r.put("setupComplete", setupMarkerService.isSetupComplete());
        r.put("versionFile", setupVersionService.resolvePath().toString());
        r.put("localVersion", setupVersionService.readLocalAppVersion().orElse(null));
        applyAdminInfoFromDatabase(r);
        if (meta != null) {
            r.putAll(meta);
        }
        return r;
    }

    private void writeCurrentAppVersionRecord() throws IOException {
        String v = setupVersionService.readLocalAppVersion().orElse("0.0.0");
        setupVersionService.writeRecordedVersion(v);
    }

    private Optional<String> fetchRemoteAppVersion() {
        return remoteUpdateService.fetchRemoteVersion();
    }

    private void applyAdminInfoFromDatabase(Map<String, Object> target) {
        try (Connection conn = DriverManager.getConnection(
                configuredDatasourceUrl, configuredDatasourceUser, configuredDatasourcePassword)) {
            if (!tableExistsInSchema(conn, DB_NAME, "admins")) {
                target.putIfAbsent("adminUsername", DEFAULT_ADMIN_USER);
                target.put("adminPasswordHint", "默认密码为 " + DEFAULT_ADMIN_PASS + "（若未修改）");
                return;
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT username FROM admins ORDER BY id ASC LIMIT 1")) {
                if (rs.next()) {
                    target.put("adminUsername", rs.getString(1));
                    target.put("adminPasswordHint", "请使用您为该账号设置的登录密码（初始安装默认为 " + DEFAULT_ADMIN_PASS + "）");
                } else {
                    target.put("adminUsername", DEFAULT_ADMIN_USER);
                    target.put("adminPasswordHint", "默认密码为 " + DEFAULT_ADMIN_PASS);
                }
            }
        } catch (Exception e) {
            target.putIfAbsent("adminUsername", DEFAULT_ADMIN_USER);
            target.put("adminPasswordHint", "默认密码为 " + DEFAULT_ADMIN_PASS);
        }
    }

    private void mergeDatabase(String host, int port, String user, String pass, Path seedSql) throws Exception {
        String tempDb = "kami_setup_temp_" + System.currentTimeMillis();
        try (Connection serverConn = openServerConnection(host, port, user, pass)) {
            createDatabaseIfAbsent(serverConn);
            try (Statement st = serverConn.createStatement()) {
                st.executeUpdate("CREATE DATABASE `" + tempDb + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
            }
        }
        try {
            importSqlFile(host, port, user, pass, tempDb, seedSql);
            try (Connection serverConn = openServerConnection(host, port, user, pass)) {
                List<String> tables = listTables(serverConn, tempDb);
                for (String table : tables) {
                    if (isMergeIgnoredTable(table)) {
                        continue;
                    }
                    boolean exists = tableExistsInSchema(serverConn, DB_NAME, table);
                    if (!exists) {
                        copyTableStructureAndData(host, port, user, pass, tempDb, DB_NAME, table, false);
                    } else {
                        copyTableStructureAndData(host, port, user, pass, tempDb, DB_NAME, table, true);
                    }
                }
            }
        } finally {
            try (Connection serverConn = openServerConnection(host, port, user, pass);
                 Statement st = serverConn.createStatement()) {
                st.executeUpdate("DROP DATABASE IF EXISTS `" + tempDb + "`");
            } catch (Exception ignored) {
            }
        }
    }

    private void copyTableStructureAndData(String host, int port, String user, String pass,
                                           String fromDb, String toDb, String table, boolean insertIgnoreOnly) throws Exception {
        if (insertIgnoreOnly) {
            if (commandExists("mysqldump") && commandExists("mysql")) {
                runShell(List.of(
                        "mysqldump", "-h" + host, "-P" + String.valueOf(port), "-u" + user,
                        "-p" + pass, "--no-create-info", "--insert-ignore", "--complete-insert",
                        fromDb, table
                ), null, toDb, host, port, user, pass);
                return;
            }
        } else {
            if (commandExists("mysqldump") && commandExists("mysql")) {
                runShell(List.of(
                        "mysqldump", "-h" + host, "-P" + String.valueOf(port), "-u" + user,
                        "-p" + pass, fromDb, table
                ), null, toDb, host, port, user, pass);
                return;
            }
        }
        throw new IllegalStateException("智能更新需要系统已安装 mysqldump 与 mysql 客户端");
    }

    private void runShell(List<String> dumpCmd, String stdin, String targetDb,
                          String host, int port, String user, String pass) throws IOException, InterruptedException {
        ProcessBuilder dump = new ProcessBuilder(dumpCmd);
        dump.redirectErrorStream(true);
        Process pDump = dump.start();
        List<String> mysqlCmd = List.of(
                "mysql", "-h" + host, "-P" + String.valueOf(port), "-u" + user, "-p" + pass, targetDb
        );
        ProcessBuilder mysql = new ProcessBuilder(mysqlCmd);
        mysql.redirectInput(ProcessBuilder.Redirect.PIPE);
        Process pMysql = mysql.start();
        try (var in = pDump.getInputStream(); var out = pMysql.getOutputStream()) {
            in.transferTo(out);
        }
        int codeDump = pDump.waitFor();
        int codeMysql = pMysql.waitFor();
        if (codeDump != 0 || codeMysql != 0) {
            throw new IOException("mysqldump/mysql 管道执行失败: dump=" + codeDump + " mysql=" + codeMysql);
        }
    }

    private void backupDatabase(String host, int port, String user, String pass) throws Exception {
        Path backupDir = Path.of("backups");
        Files.createDirectories(backupDir);
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        Path out = backupDir.resolve("kami_backup_" + ts + ".sql");
        if (commandExists("mysqldump")) {
            List<String> cmd = List.of(
                    "mysqldump", "-h" + host, "-P" + String.valueOf(port), "-u" + user, "-p" + pass,
                    "--databases", DB_NAME, "-r", out.toString()
            );
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            int code = p.waitFor();
            if (code != 0) {
                String err = new String(p.getInputStream().readAllBytes());
                throw new IOException("备份失败: " + err);
            }
        } else {
            throw new IllegalStateException("覆盖安装需要 mysqldump，请先安装 MySQL 客户端工具");
        }
    }

    private void dropAndCreateDatabase(Connection serverConn) throws Exception {
        try (Statement st = serverConn.createStatement()) {
            st.executeUpdate("DROP DATABASE IF EXISTS `" + DB_NAME + "`");
            st.executeUpdate("CREATE DATABASE `" + DB_NAME + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    private void createDatabaseIfAbsent(Connection serverConn) throws Exception {
        try (Statement st = serverConn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + DB_NAME + "` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci");
        }
    }

    private void importSqlFile(String host, int port, String user, String pass, String database, Path sqlFile) throws Exception {
        if (commandExists("mysql")) {
            List<String> cmd = List.of(
                    "mysql", "-h" + host, "-P" + String.valueOf(port), "-u" + user, "-p" + pass, database
            );
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectInput(sqlFile.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes());
            int code = p.waitFor();
            if (code == 0) {
                return;
            }
            throw new IOException("mysql 导入失败: " + output);
        }
        importSqlViaJdbc(host, port, user, pass, database, sqlFile);
    }

    private void importSqlViaJdbc(String host, int port, String user, String pass, String database, Path sqlFile) throws Exception {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&allowMultiQueries=true";
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            ScriptUtils.executeSqlScript(conn, new FileSystemResource(sqlFile.toFile()));
        }
    }

    private void updateDatasourceConfig(String host, int port, String user, String pass) throws IOException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + DB_NAME
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        List<Path> candidates = resolveApplicationPropertiesPaths();
        boolean updated = false;
        for (Path path : candidates) {
            if (!Files.isRegularFile(path)) {
                continue;
            }
            String content = Files.readString(path);
            content = replaceProperty(content, "spring.datasource.url", url);
            content = replaceProperty(content, "spring.datasource.username", user);
            content = replaceProperty(content, "spring.datasource.password", pass);
            Files.writeString(path, content);
            updated = true;
        }
        if (!updated) {
            Path fallback = Path.of("config", "application-setup.properties");
            Files.createDirectories(fallback.getParent());
            Files.writeString(fallback, """
                    spring.datasource.url=%s
                    spring.datasource.username=%s
                    spring.datasource.password=%s
                    """.formatted(url, user, pass));
        }
    }

    private String replaceProperty(String content, String key, String value) {
        String line = key + "=" + value;
        if (content.contains(key + "=")) {
            return content.replaceAll("(?m)^" + java.util.regex.Pattern.quote(key) + "=.*$", line);
        }
        return content.trim() + System.lineSeparator() + line + System.lineSeparator();
    }

    private List<Path> resolveApplicationPropertiesPaths() {
        List<Path> list = new ArrayList<>();
        Path cwd = Path.of(System.getProperty("user.dir"));
        list.add(cwd.resolve("src/main/resources/application.properties"));
        list.add(cwd.resolve("application.properties"));
        list.add(cwd.resolve("config/application.properties"));
        list.add(cwd.resolve("backend/src/main/resources/application.properties"));
        list.add(cwd.getParent() != null ? cwd.getParent().resolve("backend/src/main/resources/application.properties") : null);
        return list.stream().filter(p -> p != null).distinct().toList();
    }

    private Path resolveSeedSqlPath(String series) throws Exception {
        if ("80".equals(series) || "8".equals(series)) {
            return sqlTranslateService.resolveMysql80Seed()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "未找到 kami.sql（请使用已内置种子 SQL 的 JAR，或将 databaes/kami.sql 放在运行目录）"));
        }
        try {
            return sqlTranslateService.ensureMysql56Seed();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("5.6 脚本转译被中断");
        }
    }

    private Optional<Path> resolveSimpleCardsSql() throws IOException {
        return seedSqlLocator.resolveSimpleCardsSql();
    }

    private Connection openServerConnection(String host, int port, String user, String pass) throws Exception {
        String url = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, user, pass);
    }

    private boolean schemaExists(Connection conn, String schema) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='" + schema + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private int countTables(Connection conn, String schema) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='" + schema + "' AND TABLE_TYPE='BASE TABLE'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private List<String> listTables(Connection conn, String schema) throws Exception {
        List<String> tables = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES FROM `" + schema + "`")) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        }
        return tables;
    }

    private boolean tableExistsInSchema(Connection conn, String schema, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='" + schema + "' AND table_name='" + table + "'")) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private Map<String, Object> buildMergeRequestFromConfigured() throws Exception {
        Map<String, Object> probe = probeConfiguredDatabase();
        if (!Boolean.TRUE.equals(probe.get("reachable"))) {
            Object err = probe.get("error");
            throw new IllegalStateException("当前配置的数据库不可连接" + (err != null ? ": " + err : ""));
        }
        HostPort hp = parseJdbcHostPort(configuredDatasourceUrl);
        Map<String, Object> req = new HashMap<>();
        req.put("host", hp.host());
        req.put("port", hp.port());
        req.put("username", configuredDatasourceUser);
        req.put("password", configuredDatasourcePassword);
        req.put("sqlSeries", mysqlRuntimeProbeService.recommendSqlSeries((String) probe.get("version")));
        return req;
    }

    private HostPort parseJdbcHostPort(String jdbcUrl) {
        Matcher m = Pattern.compile("jdbc:mysql://([^/?:#]+)(?::(\\d+))?").matcher(jdbcUrl);
        if (!m.find()) {
            throw new IllegalStateException("无法解析 spring.datasource.url: " + jdbcUrl);
        }
        String host = m.group(1);
        int port = m.group(2) != null ? Integer.parseInt(m.group(2)) : 3306;
        return new HostPort(host, port);
    }

    private record HostPort(String host, int port) {}

    private Map<String, Object> probeConfiguredDatabase() {
        Map<String, Object> db = new HashMap<>();
        try {
            String url = configuredDatasourceUrl;
            try (Connection conn = DriverManager.getConnection(url, configuredDatasourceUser, configuredDatasourcePassword)) {
                db.put("reachable", true);
                try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery("SELECT VERSION()")) {
                    rs.next();
                    db.put("version", rs.getString(1));
                }
                db.put("kamiTableCount", countTables(conn, DB_NAME));
            }
        } catch (Exception e) {
            db.put("reachable", false);
            db.put("error", e.getMessage());
        }
        return db;
    }

    private Map<String, Object> probeRedis() {
        Map<String, Object> r = new HashMap<>();
        r.put("host", redisHost);
        r.put("port", redisPort);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(redisHost, redisPort), 2000);
            r.put("status", "online");
        } catch (Exception e) {
            r.put("status", "offline");
            r.put("error", e.getMessage());
        }
        return r;
    }

    private boolean isMergeIgnoredTable(String table) {
        return table != null && MERGE_IGNORE_TABLES.contains(table.toLowerCase());
    }

    private boolean commandExists(String cmd) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String str(Object o, String def) {
        return o == null || o.toString().isBlank() ? def : o.toString().trim();
    }

    private static int intVal(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
