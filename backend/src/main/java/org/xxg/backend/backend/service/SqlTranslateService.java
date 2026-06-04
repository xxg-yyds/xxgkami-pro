package org.xxg.backend.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 管理 kami.sql → MySQL 5.6 转译缓存与进度。
 */
@Service
public class SqlTranslateService {

    private final SeedSqlLocator seedSqlLocator;

    private final AtomicReference<TranslateSnapshot> snapshot = new AtomicReference<>(TranslateSnapshot.idle());
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xxgkami-sql-translate");
        t.setDaemon(true);
        return t;
    });

    @Value("${xxgkami.setup.mysql56-cache-file:data/.xxgkami-kami-mysql56.sql}")
    private String cacheFilePath;

    @Value("${xxgkami.setup.mysql56-cache-meta-file:data/.xxgkami-kami-mysql56.meta}")
    private String cacheMetaPath;

    public SqlTranslateService(SeedSqlLocator seedSqlLocator) {
        this.seedSqlLocator = seedSqlLocator;
    }

    /**
     * 异步启动转译（若缓存已是最新则立即标记完成）。
     */
    public void startTranslateAsync() {
        TranslateSnapshot current = snapshot.get();
        if ("running".equals(current.status())) {
            return;
        }
        try {
            Path source = resolveMysql80Seed().orElseThrow(() -> new IllegalStateException(seedSqlMissingMessage()));
            String sourceHash = sha256Hex(source);
            Path cache = resolveCachePath();
            if (isCacheValid(source, sourceHash, cache)) {
                snapshot.set(TranslateSnapshot.ready(100, "已使用缓存的 5.6 脚本", cache.toString(), sourceHash));
                return;
            }
        } catch (Exception e) {
            snapshot.set(TranslateSnapshot.error(e.getMessage()));
            return;
        }
        snapshot.set(TranslateSnapshot.running(0, "准备转译…", "prepare"));
        executor.submit(this::runTranslate);
    }

    /**
     * 阻塞直到 5.6 种子 SQL 可用（安装/合并前调用）。
     */
    public Path ensureMysql56Seed() throws IOException, InterruptedException {
        Path source = resolveMysql80Seed()
                .orElseThrow(() -> new IllegalStateException(seedSqlMissingMessage()));
        String sourceHash = sha256Hex(source);
        Path cache = resolveCachePath();
        if (isCacheValid(source, sourceHash, cache)) {
            snapshot.compareAndSet(TranslateSnapshot.idle(), TranslateSnapshot.ready(100, "缓存有效", cache.toString(), sourceHash));
            return cache;
        }
        TranslateSnapshot s = snapshot.get();
        if ("running".equals(s.status())) {
            waitForCompletion(120_000);
            s = snapshot.get();
            if ("ready".equals(s.status()) && s.cachePath() != null) {
                return Path.of(s.cachePath());
            }
            throw new IllegalStateException(s.error() != null ? s.error() : "转译未完成");
        }
        runTranslateSync(source, sourceHash, cache);
        return cache;
    }

    public Map<String, Object> getStatus() {
        TranslateSnapshot s = snapshot.get();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", s.status());
        m.put("percent", s.percent());
        m.put("phase", s.phase());
        m.put("message", s.message());
        m.put("ready", "ready".equals(s.status()));
        m.put("error", s.error());
        try {
            m.put("sourceFile", resolveMysql80Seed().map(Path::toString).orElse(null));
        } catch (IOException e) {
            m.put("sourceFile", null);
        }
        m.put("bundledInJar", seedSqlLocator.isBundledInJar());
        m.put("cacheFile", s.cachePath());
        m.put("sourceHash", s.sourceHash());
        m.put("updatedAt", s.updatedAt());
        return m;
    }

    public Optional<Path> resolveMysql80Seed() throws IOException {
        return seedSqlLocator.resolveKamiSql();
    }

    private void runTranslate() {
        try {
            Path source = resolveMysql80Seed().orElseThrow(() -> new IllegalStateException(seedSqlMissingMessage()));
            String sourceHash = sha256Hex(source);
            Path cache = resolveCachePath();
            if (isCacheValid(source, sourceHash, cache)) {
                snapshot.set(TranslateSnapshot.ready(100, "已使用缓存的 5.6 脚本", cache.toString(), sourceHash));
                return;
            }
            runTranslateSync(source, sourceHash, cache);
        } catch (Exception e) {
            snapshot.set(TranslateSnapshot.error(e.getMessage() != null ? e.getMessage() : "转译失败"));
        }
    }

    private void runTranslateSync(Path source, String sourceHash, Path cache) throws IOException {
        updateProgress(8, "读取 kami.sql", "read");
        String raw = Files.readString(source, StandardCharsets.UTF_8);
        updateProgress(12, "分析 MySQL 8.0 结构", "analyze");

        Mysql56SqlTransformer.TransformResult result = Mysql56SqlTransformer.transform(raw, pct -> {
            int mapped = 12 + (int) (pct * 0.78);
            String phase = pct < 40 ? "collation" : pct < 55 ? "columns" : pct < 70 ? "indexes" : "finalize";
            String msg = switch (phase) {
                case "collation" -> "替换 8.0 排序规则…";
                case "columns" -> "调整索引列长度…";
                case "indexes" -> "修复唯一索引前缀…";
                default -> "生成 5.6 脚本…";
            };
            updateProgress(mapped, msg, phase);
        });

        updateProgress(92, "写入转译结果", "write");
        Path cacheParent = cache.getParent();
        if (cacheParent != null) {
            Files.createDirectories(cacheParent);
        }
        Files.writeString(cache, result.sql(), StandardCharsets.UTF_8);
        Path meta = resolveCacheMetaPath();
        if (meta.getParent() != null) {
            Files.createDirectories(meta.getParent());
        }
        Files.writeString(meta, sourceHash + System.lineSeparator() + source.toString(), StandardCharsets.UTF_8);

        String summary = String.format(
                "转译完成（排序规则 %d 处，card_hash %d 处，唯一索引 %d 处）",
                result.collationReplacements(),
                result.cardHashColumnFixes(),
                result.uniqueIndexPrefixFixes()
        );
        snapshot.set(TranslateSnapshot.ready(100, summary, cache.toString(), sourceHash));
    }

    private void updateProgress(int percent, String message, String phase) {
        snapshot.set(TranslateSnapshot.running(percent, message, phase));
    }

    private void waitForCompletion(long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            TranslateSnapshot s = snapshot.get();
            if ("ready".equals(s.status()) || "error".equals(s.status())) {
                return;
            }
            Thread.sleep(200);
        }
    }

    private boolean isCacheValid(Path source, String sourceHash, Path cache) throws IOException {
        if (!Files.isRegularFile(cache)) {
            return false;
        }
        Path meta = resolveCacheMetaPath();
        if (!Files.isRegularFile(meta)) {
            return false;
        }
        String firstLine = Files.readString(meta, StandardCharsets.UTF_8).lines().findFirst().orElse("");
        return sourceHash.equals(firstLine.trim()) && Files.getLastModifiedTime(source).toMillis()
                <= Files.getLastModifiedTime(cache).toMillis();
    }

    private Path resolveCachePath() {
        return Path.of(cacheFilePath).toAbsolutePath().normalize();
    }

    private Path resolveCacheMetaPath() {
        return Path.of(cacheMetaPath).toAbsolutePath().normalize();
    }

    private static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(Files.readAllBytes(file));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IOException("计算文件摘要失败", e);
        }
    }

    private String seedSqlMissingMessage() {
        return "未找到 kami.sql（请将 databaes/kami.sql 放在运行目录，或使用已内置种子 SQL 的 JAR 包）";
    }

    private record TranslateSnapshot(
            String status,
            int percent,
            String phase,
            String message,
            String error,
            String cachePath,
            String sourceHash,
            long updatedAt
    ) {
        static TranslateSnapshot idle() {
            return new TranslateSnapshot("idle", 0, "", "等待转译", null, null, null, Instant.now().toEpochMilli());
        }

        static TranslateSnapshot running(int percent, String message, String phase) {
            return new TranslateSnapshot("running", percent, phase, message, null, null, null, Instant.now().toEpochMilli());
        }

        static TranslateSnapshot ready(int percent, String message, String cachePath, String sourceHash) {
            return new TranslateSnapshot("ready", percent, "done", message, null, cachePath, sourceHash, Instant.now().toEpochMilli());
        }

        static TranslateSnapshot error(String error) {
            return new TranslateSnapshot("error", 0, "error", error, error, null, null, Instant.now().toEpochMilli());
        }
    }
}
