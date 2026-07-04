package org.xxg.backend.backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.xxg.backend.backend.filter.RequestMonitorFilter;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 系统监控服务
 */
@Service
public class SystemMonitorService {

    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private OnlineUserService onlineUserService;

    @Autowired
    private RequestMonitorFilter requestMonitorFilter;

    /** Linux /proc/stat 上次采样，用于计算各核心 CPU 使用率 */
    private long[] prevCoreIdle;
    private long[] prevCoreTotal;
    private int prevCoreCount = 0;

    // 用于计算QPS
    private long lastQuestionsCount = 0;
    private long lastCheckTime = 0;

    /**
     * 获取数据库状态信息
     */
    public Map<String, Object> getDatabaseStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // 测试数据库连接
            long startTime = System.currentTimeMillis();
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            long responseTime = System.currentTimeMillis() - startTime;
            
            status.put("status", "online");
            status.put("responseTime", responseTime);
            
            // 获取数据库信息
            try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
                DatabaseMetaData metaData = connection.getMetaData();
                status.put("databaseName", metaData.getDatabaseProductName());
                status.put("databaseVersion", metaData.getDatabaseProductVersion());
                status.put("driverName", metaData.getDriverName());
                status.put("driverVersion", metaData.getDriverVersion());
                status.put("url", metaData.getURL());
            }
            
            // 获取活跃连接数
            try {
                String connectionSql = "SHOW STATUS LIKE 'Threads_connected'";
                List<Map<String, Object>> result = jdbcTemplate.queryForList(connectionSql);
                if (!result.isEmpty()) {
                    String value = result.get(0).get("Value").toString();
                    status.put("activeConnections", Integer.parseInt(value));
                } else {
                    status.put("activeConnections", 0);
                }
            } catch (Exception e) {
                status.put("activeConnections", 0);
            }
            
            // 获取最大连接数
             try {
                String maxConnectionSql = "SHOW VARIABLES LIKE 'max_connections'";
                List<Map<String, Object>> result = jdbcTemplate.queryForList(maxConnectionSql);
                if (!result.isEmpty()) {
                    String value = result.get(0).get("Value").toString();
                    status.put("maxConnections", Integer.parseInt(value));
                } else {
                    status.put("maxConnections", 100);
                }
            } catch (Exception e) {
                status.put("maxConnections", 100);
            }
            
            // 获取QPS (Queries per second)
            try {
                 String qpsSql = "SHOW STATUS LIKE 'Questions'";
                 List<Map<String, Object>> result = jdbcTemplate.queryForList(qpsSql);
                 if (!result.isEmpty()) {
                     long currentQuestions = Long.parseLong(result.get(0).get("Value").toString());
                     long currentTime = System.currentTimeMillis();
                     
                     if (lastCheckTime > 0 && currentTime > lastCheckTime) {
                         long timeDiff = currentTime - lastCheckTime; // ms
                         long countDiff = currentQuestions - lastQuestionsCount;
                         
                         // 避免负数（如果数据库重启了）
                         if (countDiff < 0) countDiff = 0;
                         
                         double qps = (double) countDiff * 1000 / timeDiff;
                         status.put("qps", String.format("%.2f", qps));
                     } else {
                         status.put("qps", "Calculated next time");
                     }
                     
                     lastQuestionsCount = currentQuestions;
                     lastCheckTime = currentTime;
                 } else {
                     status.put("qps", "N/A");
                 }
            } catch (Exception e) {
                status.put("qps", "N/A");
            }
            
            // 获取数据库大小信息
            try {
                // 查询数据库大小（MySQL）
                String sizeQuery = "SELECT ROUND(SUM(data_length + index_length) / 1024 / 1024, 2) AS 'DB Size in MB' " +
                                 "FROM information_schema.tables WHERE table_schema = DATABASE()";
                Double dbSize = jdbcTemplate.queryForObject(sizeQuery, Double.class);
                status.put("databaseSize", dbSize != null ? dbSize + " MB" : "Unknown");
            } catch (Exception e) {
                status.put("databaseSize", "Unknown");
            }
            
            // 获取表数量
            try {
                String tableCountQuery = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()";
                Integer tableCount = jdbcTemplate.queryForObject(tableCountQuery, Integer.class);
                status.put("tableCount", tableCount);
            } catch (Exception e) {
                status.put("tableCount", 0);
            }
            
        } catch (Exception e) {
            status.put("status", "offline");
            status.put("error", e.getMessage());
            status.put("responseTime", "N/A");
            status.put("activeConnections", 0);
            status.put("maxConnections", 0);
        }
        
        status.put("lastCheck", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return status;
    }

    /**
     * 获取系统资源状态
     */
    public Map<String, Object> getSystemStatus() {
        Map<String, Object> status = new HashMap<>();

        try {
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            int processors = osBean.getAvailableProcessors();
            status.put("status", "online");
            status.put("osName", osBean.getName());
            status.put("osVersion", osBean.getVersion());
            status.put("osArch", osBean.getArch());
            status.put("availableProcessors", processors);
            status.put("uptime", getUptime());
            status.put("javaVersion", System.getProperty("java.version"));

            com.sun.management.OperatingSystemMXBean sunOs = null;
            if (osBean instanceof com.sun.management.OperatingSystemMXBean) {
                sunOs = (com.sun.management.OperatingSystemMXBean) osBean;
            }

            double systemCpu = 0;
            double processCpu = 0;
            if (sunOs != null) {
                double raw = sunOs.getCpuLoad();
                if (raw >= 0) {
                    systemCpu = raw * 100;
                }
                double proc = sunOs.getProcessCpuLoad();
                if (proc >= 0) {
                    processCpu = proc * 100;
                }
            }
            status.put("cpuUsage", round2(systemCpu));
            status.put("processCpuUsage", round2(processCpu));

            // 运行负载
            Map<String, Object> load = readLoadAverage(processors, systemCpu);
            status.put("load", load);

            // CPU 各核心负载
            List<Map<String, Object>> cores = readCpuCoreUsage(processors, systemCpu);
            status.put("cpuCores", cores);

            // 物理内存 + JVM
            Map<String, Object> memory = new LinkedHashMap<>();
            if (sunOs != null) {
                long totalPhys = sunOs.getTotalMemorySize();
                long freePhys = sunOs.getFreeMemorySize();
                long usedPhys = totalPhys - freePhys;
                double physPct = totalPhys > 0 ? usedPhys * 100.0 / totalPhys : 0;
                memory.put("usagePercent", round2(physPct));
                memory.put("used", formatBytes(usedPhys));
                memory.put("total", formatBytes(totalPhys));
                memory.put("free", formatBytes(freePhys));
            } else {
                memory.put("usagePercent", 0);
                memory.put("used", "N/A");
                memory.put("total", "N/A");
                memory.put("free", "N/A");
            }
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            long jvmUsed = memoryBean.getHeapMemoryUsage().getUsed();
            long jvmMax = memoryBean.getHeapMemoryUsage().getMax();
            memory.put("jvmUsed", formatBytes(jvmUsed));
            memory.put("jvmMax", formatBytes(jvmMax));
            memory.put("jvmUsagePercent", jvmMax > 0 ? round2(jvmUsed * 100.0 / jvmMax) : 0);
            status.put("memory", memory);
            status.put("memoryUsage", memory.get("usagePercent"));

            // 磁盘（应用所在盘）
            File root = resolveDiskRoot();
            Map<String, Object> disk = new LinkedHashMap<>();
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usedSpace = totalSpace - freeSpace;
            double diskPct = totalSpace > 0 ? usedSpace * 100.0 / totalSpace : 0;
            disk.put("usagePercent", round2(diskPct));
            disk.put("used", formatBytes(usedSpace));
            disk.put("total", formatBytes(totalSpace));
            disk.put("free", formatBytes(freeSpace));
            disk.put("path", root.getAbsolutePath());
            status.put("disk", disk);
            status.put("diskUsage", disk.get("usagePercent"));

            double peak = Math.max(systemCpu, physPct(memory));
            if (peak >= 85 || loadLevelScore(load) >= 85) {
                status.put("health", "warning");
            } else if (peak >= 70 || loadLevelScore(load) >= 70) {
                status.put("health", "moderate");
            } else {
                status.put("health", "healthy");
            }

        } catch (Exception e) {
            status.put("status", "offline");
            status.put("error", e.getMessage());
        }

        status.put("lastCheck", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return status;
    }

    private static double physPct(Map<String, Object> memory) {
        Object v = memory.get("usagePercent");
        return v instanceof Number ? ((Number) v).doubleValue() : 0;
    }

    private static double loadLevelScore(Map<String, Object> load) {
        Object v = load.get("load1");
        return v instanceof Number ? ((Number) v).doubleValue() * 100 : 0;
    }

    private File resolveDiskRoot() {
        String userDir = System.getProperty("user.dir", ".");
        File dir = new File(userDir);
        if (dir.exists()) {
            return dir;
        }
        return new File(".");
    }

    private Map<String, Object> readLoadAverage(int processors, double systemCpuFallback) {
        Map<String, Object> load = new LinkedHashMap<>();
        Path proc = Path.of("/proc/loadavg");
        if (Files.isRegularFile(proc)) {
            try {
                String[] parts = Files.readString(proc, StandardCharsets.UTF_8).trim().split("\\s+");
                if (parts.length >= 3) {
                    double l1 = Double.parseDouble(parts[0]);
                    double l5 = Double.parseDouble(parts[1]);
                    double l15 = Double.parseDouble(parts[2]);
                    load.put("load1", round2(l1));
                    load.put("load5", round2(l5));
                    load.put("load15", round2(l15));
                    load.put("loadPercent", round2(Math.min(100, l1 / Math.max(1, processors) * 100)));
                    load.put("level", loadLevelLabel(l1, processors));
                    load.put("source", "linux");
                    return load;
                }
            } catch (IOException | NumberFormatException ignored) {
            }
        }
        double pseudo = systemCpuFallback / 100.0 * processors;
        load.put("load1", round2(pseudo));
        load.put("load5", round2(pseudo));
        load.put("load15", round2(pseudo));
        load.put("loadPercent", round2(systemCpuFallback));
        load.put("level", loadLevelLabel(pseudo, processors));
        load.put("source", "estimated");
        return load;
    }

    private String loadLevelLabel(double load1, int processors) {
        double ratio = load1 / Math.max(1, processors);
        if (ratio < 0.5) {
            return "低";
        }
        if (ratio < 0.85) {
            return "中";
        }
        return "高";
    }

    private List<Map<String, Object>> readCpuCoreUsage(int processors, double systemCpuFallback) {
        List<Map<String, Object>> cores = new ArrayList<>();
        if (readLinuxProcStatCores(cores, processors)) {
            return cores;
        }
        for (int i = 0; i < processors; i++) {
            Map<String, Object> core = new HashMap<>();
            core.put("index", i);
            core.put("label", "CPU " + i);
            core.put("usage", round2(systemCpuFallback));
            cores.add(core);
        }
        return cores;
    }

    private boolean readLinuxProcStatCores(List<Map<String, Object>> cores, int processors) {
        Path stat = Path.of("/proc/stat");
        if (!Files.isRegularFile(stat)) {
            return false;
        }
        try {
            List<String> lines = Files.readAllLines(stat, StandardCharsets.UTF_8);
            List<long[]> samples = new ArrayList<>();
            for (String line : lines) {
                if (!line.matches("cpu\\d+\\s+.*")) {
                    continue;
                }
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5) {
                    continue;
                }
                long idle = Long.parseLong(parts[4]);
                long total = 0;
                for (int i = 1; i < parts.length; i++) {
                    total += Long.parseLong(parts[i]);
                }
                samples.add(new long[]{idle, total});
            }
            if (samples.isEmpty()) {
                return false;
            }
            int n = samples.size();
            if (prevCoreIdle == null || prevCoreIdle.length != n) {
                prevCoreIdle = new long[n];
                prevCoreTotal = new long[n];
                for (int i = 0; i < n; i++) {
                    prevCoreIdle[i] = samples.get(i)[0];
                    prevCoreTotal[i] = samples.get(i)[1];
                }
                prevCoreCount = n;
                for (int i = 0; i < n; i++) {
                    Map<String, Object> core = new HashMap<>();
                    core.put("index", i);
                    core.put("label", "CPU " + i);
                    core.put("usage", 0);
                    cores.add(core);
                }
                return true;
            }
            for (int i = 0; i < n; i++) {
                long idle = samples.get(i)[0];
                long total = samples.get(i)[1];
                long idleDelta = idle - prevCoreIdle[i];
                long totalDelta = total - prevCoreTotal[i];
                double usage = 0;
                if (totalDelta > 0) {
                    usage = (totalDelta - idleDelta) * 100.0 / totalDelta;
                }
                prevCoreIdle[i] = idle;
                prevCoreTotal[i] = total;
                Map<String, Object> core = new HashMap<>();
                core.put("index", i);
                core.put("label", "CPU " + i);
                core.put("usage", round2(Math.max(0, Math.min(100, usage))));
                cores.add(core);
            }
            return true;
        } catch (IOException | NumberFormatException e) {
            return false;
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /**
     * 获取API服务状态
     */
    public Map<String, Object> getApiStatus() {
        Map<String, Object> status = new HashMap<>();
        
        status.put("status", "online");
        status.put("uptime", getUptime());
        
        // 使用RequestMonitorFilter获取真实数据
        long totalRequests = requestMonitorFilter.getTotalRequests();
        long totalErrors = requestMonitorFilter.getTotalErrors();
        double avgResponseTime = requestMonitorFilter.getAvgResponseTime();
        
        status.put("requestCount", totalRequests);
        
        double errorRate = 0;
        if (totalRequests > 0) {
            errorRate = (double) totalErrors / totalRequests * 100;
        }
        
        // 成功率 = 100 - 错误率
        status.put("successRate", Math.round((100 - errorRate) * 100.0) / 100.0);
        status.put("errorRate", Math.round(errorRate * 100.0) / 100.0);
        status.put("errorCount", totalErrors);
        status.put("avgResponseTime", Math.round(avgResponseTime * 100.0) / 100.0);
        
        status.put("lastCheck", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        return status;
    }

    /**
     * 获取在线用户信息
     */
    public Map<String, Object> getOnlineUsers() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 使用OnlineUserService获取真实的在线用户信息
            Map<String, Object> onlineInfo = onlineUserService.getOnlineUsersInfo();
            result.putAll(onlineInfo);
            
            // 查询总用户数
            String totalUsersQuery = "SELECT COUNT(*) FROM users WHERE status = 1";
            Integer totalUsers = jdbcTemplate.queryForObject(totalUsersQuery, Integer.class);
            result.put("totalUsers", totalUsers != null ? totalUsers : 0);
            
            // 获取最近活跃的用户列表（从数据库）
            String recentUsersQuery = "SELECT username, nickname, last_login_time FROM users " +
                                    "WHERE last_login_time > DATE_SUB(NOW(), INTERVAL 2 HOUR) " +
                                    "ORDER BY last_login_time DESC LIMIT 10";
            
            List<Map<String, Object>> recentUsers = jdbcTemplate.queryForList(recentUsersQuery);
            result.put("recentUsers", recentUsers);
            
        } catch (Exception e) {
            result.put("onlineCount", 0);
            result.put("totalUsers", 0);
            result.put("onlineUsers", new ArrayList<>());
            result.put("recentUsers", new ArrayList<>());
            result.put("error", e.getMessage());
        }
        
        result.put("lastCheck", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        return result;
    }

    /**
     * 获取所有监控数据
     */
    public Map<String, Object> getAllMonitorData() {
        Map<String, Object> allData = new HashMap<>();
        
        allData.put("database", getDatabaseStatus());
        allData.put("system", getSystemStatus());
        allData.put("api", getApiStatus());
        allData.put("users", getOnlineUsers());
        allData.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        return allData;
    }

    /**
     * 格式化字节数
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * 获取系统运行时间
     */
    private String getUptime() {
        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptime / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;
        
        if (days > 0) {
            return String.format("%d天 %d小时", days, hours % 24);
        } else if (hours > 0) {
            return String.format("%d小时 %d分钟", hours, minutes % 60);
        } else {
            return String.format("%d分钟", minutes);
        }
    }

    /**
     * 生成随机数
     */
    private int getRandomNumber(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }
}