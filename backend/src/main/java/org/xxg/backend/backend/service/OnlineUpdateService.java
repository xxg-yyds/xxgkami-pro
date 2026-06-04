package org.xxg.backend.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 在线更新：下载 Release 制品、替换 JAR 与 dist，并调度进程重启。
 */
@Service
public class OnlineUpdateService {

    private final RemoteUpdateService remoteUpdateService;
    private final SetupVersionService setupVersionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "xxgkami-online-update");
        t.setDaemon(true);
        return t;
    });

    private final AtomicReference<Map<String, Object>> status = new AtomicReference<>(idleStatus());

    @Value("${xxgkami.update.cache-dir:data/.xxgkami-update-cache}")
    private String cacheDir;

    @Value("${xxgkami.update.frontend-dist-path:}")
    private String configuredDistPath;

    @Value("${xxgkami.update.backend-jar-path:}")
    private String configuredJarPath;

    @Value("${xxgkami.update.backend-jar-names:backend-0.0.1-SNAPSHOT.jar,backend.jar}")
    private String backendJarNames;

    @Value("${xxgkami.update.backend-jar-prefix:backend-0.0.1-SNAPSHOT}")
    private String backendJarPrefix;

    @Value("${xxgkami.update.frontend-dist-dir-name:dist}")
    private String frontendDistDirName;

    @Value("${xxgkami.update.path-search-depth:5}")
    private int pathSearchDepth;

    @Value("${xxgkami.update.deploy-roots:/www/wwwroot}")
    private String deployRoots;

    public OnlineUpdateService(RemoteUpdateService remoteUpdateService, SetupVersionService setupVersionService) {
        this.remoteUpdateService = remoteUpdateService;
        this.setupVersionService = setupVersionService;
    }

    public Map<String, Object> detectPaths() {
        try {
            return detectPathsInternal();
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("backendJarPath", defaultJarGuess());
            fallback.put("frontendDistPath", defaultDistGuess());
            fallback.put("userDir", System.getProperty("user.dir"));
            fallback.put("os", System.getProperty("os.name"));
            fallback.put("localVersion", setupVersionService.readLocalAppVersion().orElse("0.0.0"));
            fallback.put("jarDetected", false);
            fallback.put("frontendDistDetected", false);
            fallback.put("jarExists", Files.isRegularFile(Path.of(fallback.get("backendJarPath").toString())));
            fallback.put("distExists", isValidDistDir(Path.of(fallback.get("frontendDistPath").toString())));
            fallback.put("defaultBackendJarName", primaryBackendJarName());
            fallback.put("defaultFrontendDirName", frontendDistDirName.trim());
            fallback.put("backendJarMatchRule", "检测异常，已回退默认路径");
            fallback.put("frontendDistMatchRule", "检测异常，已回退默认路径");
            fallback.put("detectError", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            return fallback;
        }
    }

    private Map<String, Object> detectPathsInternal() {
        Map<String, Object> m = new LinkedHashMap<>();
        PathMatch jarMatch = resolveBackendJarMatch();
        PathMatch distMatch = resolveFrontendDistMatch();
        if (distMatch == null && jarMatch != null) {
            distMatch = findFrontendDistNearJar(jarMatch.path());
        }
        String jar = jarMatch != null ? jarMatch.path().toString() : defaultJarGuess();
        String dist = distMatch != null ? distMatch.path().toString() : suggestDistPath(jarMatch);
        m.put("backendJarPath", jar);
        m.put("frontendDistPath", dist);
        m.put("userDir", System.getProperty("user.dir"));
        m.put("os", System.getProperty("os.name"));
        m.put("localVersion", setupVersionService.readLocalAppVersion().orElse("0.0.0"));
        m.put("jarDetected", jarMatch != null);
        m.put("frontendDistDetected", distMatch != null);
        m.put("jarExists", Files.isRegularFile(Path.of(jar)));
        m.put("distExists", isValidDistDir(Path.of(dist)));
        m.put("defaultBackendJarName", primaryBackendJarName());
        m.put("defaultFrontendDirName", frontendDistDirName.trim());
        m.put("deploySearchRoots", preferredDeployRoots().stream().map(Path::toString).toList());
        m.put("backendJarMatchRule", jarMatch != null ? jarMatch.rule() : "未自动匹配，请填写或点重新检测");
        m.put("frontendDistMatchRule", distMatch != null ? distMatch.rule() : "未自动匹配（需含 index.html），请填写实际站点目录");
        if (jarMatch != null) {
            m.put("backendJarMatchedName", pathFileName(jarMatch.path()));
        }
        if (distMatch != null) {
            m.put("frontendDistMatchedName", pathFileName(distMatch.path()));
        }
        return m;
    }

    private record PathMatch(Path path, String rule) {
    }

    public Map<String, Object> getStatus() {
        return new LinkedHashMap<>(status.get());
    }

    public synchronized void startUpdate(String backendJarPath, String distPath) throws Exception {
        Map<String, Object> current = status.get();
        if ("running".equals(current.get("status"))) {
            throw new IllegalStateException("已有更新任务正在执行");
        }

        Path jar = Path.of(backendJarPath).toAbsolutePath().normalize();
        Path dist = Path.of(distPath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalArgumentException("后端 JAR 路径无效: " + jar);
        }
        if (!isValidDistDir(dist)) {
            throw new IllegalArgumentException("前端 dist 目录无效（需包含 index.html）: " + dist);
        }

        RemoteUpdateService.FetchedVersion fetched = remoteUpdateService.fetchBestRemoteVersionJson()
                .orElseThrow(() -> new IllegalStateException("无法获取远程 version.json"));
        String jsonBody = fetched.json();
        List<RemoteUpdateService.ReleaseArtifacts> downloadPlans = remoteUpdateService.resolveDownloadPlans(jsonBody);
        if (downloadPlans.isEmpty()) {
            throw new IllegalStateException(
                    "version.json 缺少 releaseDownloads，且无法生成下载地址；"
                            + "请确认已在 Gitee/GitHub Release 上传 dist.zip 与 backend JAR（标签如 1.0.7 或 v1.0.7）");
        }

        RemoteUpdateService.Channel downloadChannel = remoteUpdateService.detectPreferredChannel();
        String localVersion = setupVersionService.readLocalAppVersion().orElse("0.0.0");
        String remoteVersion = remoteUpdateService.parseVersion(jsonBody).orElseThrow(
                () -> new IllegalStateException("无法解析远程版本号"));
        if (setupVersionService.compareVersions(localVersion, remoteVersion) >= 0) {
            throw new IllegalStateException("当前版本已不低于远程版本 v" + remoteVersion);
        }

        updateStatus("running", 0, "准备更新…", "prepare", downloadChannel.name().toLowerCase(), remoteVersion);

        executor.submit(() -> runUpdate(jar, dist, downloadPlans, downloadChannel.name().toLowerCase(), remoteVersion));
    }

    private void runUpdate(Path jar, Path dist, List<RemoteUpdateService.ReleaseArtifacts> downloadPlans,
                           String channel, String remoteVersion) {
        Path cache = Path.of(cacheDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(cache);
            Path jarZip = cache.resolve("backend-0.0.1-SNAPSHOT.jar.download");
            Path distZip = cache.resolve("dist.zip.download");

            List<String> jarUrls = downloadPlans.stream()
                    .map(RemoteUpdateService.ReleaseArtifacts::backendJarUrl)
                    .distinct()
                    .toList();
            List<String> distUrls = downloadPlans.stream()
                    .map(RemoteUpdateService.ReleaseArtifacts::distZipUrl)
                    .distinct()
                    .toList();

            updateStatus("running", 5, "下载后端 JAR…", "download_jar", channel, remoteVersion);
            downloadFileWithFallback(jarUrls, jarZip, true,
                    pct -> updateStatus("running", 5 + pct / 4, "下载后端 JAR…", "download_jar", channel, remoteVersion),
                    (url, ch) -> updateStatus("running", 5, "尝试 " + ch + " 下载 JAR…", "download_jar", channel, remoteVersion, url));

            updateStatus("running", 30, "下载前端 dist.zip…", "download_dist", channel, remoteVersion);
            downloadFileWithFallback(distUrls, distZip, false,
                    pct -> updateStatus("running", 30 + pct / 4, "下载前端 dist.zip…", "download_dist", channel, remoteVersion),
                    (url, ch) -> updateStatus("running", 30, "尝试 " + ch + " 下载 dist…", "download_dist", channel, remoteVersion, url));

            updateStatus("running", 55, "备份并替换后端 JAR…", "replace_jar", channel, remoteVersion);
            backupAndReplaceJar(jar, jarZip);

            updateStatus("running", 70, "解压并覆盖前端 dist…", "replace_dist", channel, remoteVersion);
            unzipToDist(distZip, dist);

            updateStatus("running", 85, "清理临时文件…", "cleanup", channel, remoteVersion);
            Files.deleteIfExists(jarZip);
            Files.deleteIfExists(distZip);

            updateStatus("running", 90, "调度后端重启…", "restart", channel, remoteVersion);
            scheduleRestart(jar);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("status", "done");
            done.put("percent", 100);
            done.put("message", "更新完成，后端正在重启，请约 10 秒后刷新页面");
            done.put("baotaHint",
                    "若刷新后仍无法访问，可能因宝塔权限导致后端未能自动启动，请前往：宝塔 → 网站 → Java 项目，手动启动后端 JAR 文件。");
            done.put("phase", "done");
            done.put("channel", channel);
            done.put("remoteVersion", remoteVersion);
            done.put("finishedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            status.set(done);
        } catch (Exception e) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("status", "error");
            err.put("percent", status.get().getOrDefault("percent", 0));
            err.put("message", e.getMessage() != null ? e.getMessage() : "在线更新失败");
            err.put("phase", "error");
            err.put("channel", channel);
            err.put("remoteVersion", remoteVersion);
            status.set(err);
        }
    }

    private void backupAndReplaceJar(Path jar, Path downloaded) throws IOException {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String jarBaseName = pathFileNameOr(jar, primaryBackendJarName());
        Path backup = jar.resolveSibling(jarBaseName + ".bak." + ts);
        Files.copy(jar, backup, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(downloaded, jar, StandardCopyOption.REPLACE_EXISTING);
    }

    private void unzipToDist(Path zipFile, Path distDir) throws IOException {
        Path temp = Path.of(cacheDir).toAbsolutePath().normalize()
                .resolve("dist-extract-" + System.currentTimeMillis());
        unzip(zipFile, temp);
        Path source = resolveDistRootInsideZip(temp);
        try (Stream<Path> walk = Files.walk(source)) {
            walk.forEach(src -> {
                try {
                    if (Files.isDirectory(src)) {
                        return;
                    }
                    Path rel = source.relativize(src);
                    Path dest = distDir.resolve(rel.toString());
                    Files.createDirectories(dest.getParent());
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } finally {
            deleteRecursive(temp);
        }
    }

    private Path resolveDistRootInsideZip(Path temp) {
        if (Files.exists(temp.resolve("dist/index.html"))) {
            return temp.resolve("dist");
        }
        if (Files.exists(temp.resolve("index.html"))) {
            return temp;
        }
        try (Stream<Path> s = Files.list(temp)) {
            List<Path> dirs = s.filter(Files::isDirectory).toList();
            if (dirs.size() == 1 && Files.exists(dirs.get(0).resolve("index.html"))) {
                return dirs.get(0);
            }
        } catch (IOException ignored) {
        }
        return temp;
    }

    private void unzip(Path zipFile, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buf = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                Path out = destDir.resolve(entry.getName()).normalize();
                if (!out.startsWith(destDir)) {
                    throw new IOException("非法 zip 路径: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (OutputStream os = Files.newOutputStream(out)) {
                        int n;
                        while ((n = zis.read(buf)) > 0) {
                            os.write(buf, 0, n);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void scheduleRestart(Path jar) throws IOException {
        long pid = ProcessHandle.current().pid();
        String javaBin = Path.of(System.getProperty("java.home")).resolve("bin/java").toString();
        List<String> jvmArgs = new ArrayList<>();
        for (String arg : java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (arg.startsWith("-X") || arg.startsWith("-D") || arg.startsWith("-javaagent")) {
                jvmArgs.add(arg);
            }
        }
        Path workDir = jar.getParent() != null ? jar.getParent() : Path.of(".").toAbsolutePath();
        Path scriptDir = Path.of(cacheDir).toAbsolutePath().normalize();
        Files.createDirectories(scriptDir);

        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path script = scriptDir.resolve(windows ? "restart-backend.bat" : "restart-backend.sh");
        String scriptBody = windows ? buildWindowsRestartScript(javaBin, jvmArgs, jar, workDir, pid)
                : buildUnixRestartScript(javaBin, jvmArgs, jar, workDir, pid);
        Files.writeString(script, scriptBody);
        if (!windows) {
            script.toFile().setExecutable(true);
        }

        ProcessBuilder pb = windows
                ? new ProcessBuilder("cmd.exe", "/c", "start", "", script.toString())
                : new ProcessBuilder("/bin/sh", script.toString());
        pb.directory(workDir.toFile());
        pb.inheritIO();
        pb.start();
    }

    private String buildUnixRestartScript(String javaBin, List<String> jvmArgs, Path jar, Path workDir, long pid) {
        StringBuilder sb = new StringBuilder();
        sb.append("#!/bin/sh\n");
        sb.append("sleep 3\n");
        sb.append("kill ").append(pid).append(" 2>/dev/null || true\n");
        sb.append("sleep 1\n");
        sb.append("cd \"").append(workDir).append("\"\n");
        sb.append("nohup \"").append(javaBin).append("\"");
        for (String a : jvmArgs) {
            sb.append(" \"").append(a.replace("\"", "\\\"")).append("\"");
        }
        sb.append(" -jar \"").append(jar).append("\" >> logs/restart.log 2>&1 &\n");
        return sb.toString();
    }

    private String buildWindowsRestartScript(String javaBin, List<String> jvmArgs, Path jar, Path workDir, long pid) {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\n");
        sb.append("timeout /t 3 /nobreak >nul\n");
        sb.append("taskkill /PID ").append(pid).append(" /F >nul 2>&1\n");
        sb.append("cd /d \"").append(workDir).append("\"\n");
        sb.append("start \"\" \"").append(javaBin).append("\"");
        for (String a : jvmArgs) {
            sb.append(" ").append(a);
        }
        sb.append(" -jar \"").append(jar).append("\"\n");
        return sb.toString();
    }

    private static final int MIN_JAR_BYTES = 50_000;
    private static final int MIN_ZIP_BYTES = 5_000;

    @FunctionalInterface
    private interface DownloadAttemptListener {
        void onAttempt(String url, String channelLabel);
    }

    private void downloadFileWithFallback(List<String> urls, Path target, boolean expectJar,
                                          IntConsumer onPercent, DownloadAttemptListener onAttempt) throws IOException {
        if (urls.isEmpty()) {
            throw new IOException("没有可用的下载地址");
        }
        IOException last = null;
        for (String url : urls) {
            String ch = url.contains("gitee.com") ? "Gitee" : (url.contains("github.com") ? "GitHub" : "镜像");
            try {
                if (onAttempt != null) {
                    onAttempt.onAttempt(url, ch);
                }
                downloadFile(url, target, onPercent);
                validateDownloadedFile(target, expectJar);
                return;
            } catch (IOException e) {
                last = e;
                Files.deleteIfExists(target);
            }
        }
        String hint = "请确认 Release 已发布且附件名正确（dist.zip、backend-0.0.1-SNAPSHOT.jar），"
                + "标签与 version 一致（如 1.0.7 或 v1.0.7）；国内服务器建议优先使用 Gitee Release。";
        throw new IOException((last != null ? last.getMessage() : "下载失败") + "。已尝试 " + urls.size() + " 个地址。" + hint, last);
    }

    private void validateDownloadedFile(Path target, boolean expectJar) throws IOException {
        long size = Files.size(target);
        int min = expectJar ? MIN_JAR_BYTES : MIN_ZIP_BYTES;
        if (size < min) {
            throw new IOException("文件过小(" + size + " 字节)，Release 可能不存在或返回了错误页面");
        }
        try (InputStream in = Files.newInputStream(target)) {
            int b0 = in.read();
            int b1 = in.read();
            if (b0 != 'P' || b1 != 'K') {
                throw new IOException("不是有效的 ZIP/JAR 文件");
            }
        }
    }

    private void downloadFile(String url, Path target, IntConsumer onPercent) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(30_000);
        conn.setReadTimeout(600_000);
        conn.setRequestProperty("User-Agent", "xxgkami-online-update/1.0");
        int code = conn.getResponseCode();
        if (code >= 400) {
            conn.disconnect();
            throw new IOException("HTTP " + code + " ← " + url);
        }
        long total = conn.getContentLengthLong();
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream in = conn.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] buf = new byte[8192];
            long done = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                done += n;
                if (total > 0) {
                    onPercent.accept((int) Math.min(100, done * 100 / total));
                }
            }
        } finally {
            conn.disconnect();
        }
    }

    private PathMatch resolveBackendJarMatch() {
        if (configuredJarPath != null && !configuredJarPath.isBlank()) {
            Path p = Path.of(configuredJarPath).toAbsolutePath().normalize();
            return new PathMatch(p, "配置项 xxgkami.update.backend-jar-path");
        }
        PathMatch running = resolveRunningJarPath();
        if (running != null) {
            return running;
        }
        PathMatch deployMatch = findBackendJarUnderRoots(preferredDeployRoots(), "宝塔部署目录 /www/wwwroot");
        if (deployMatch != null) {
            return deployMatch;
        }
        String cp = System.getProperty("java.class.path", "");
        for (String part : cp.split(System.getProperty("path.separator"))) {
            if (part == null || part.isBlank()) {
                continue;
            }
            Path candidate = Path.of(part).toAbsolutePath().normalize();
            if (pathFileName(candidate).toLowerCase().endsWith(".jar") && Files.isRegularFile(candidate)) {
                return new PathMatch(candidate, "java.class.path");
            }
        }
        for (String jarName : backendJarNameList()) {
            for (Path root : collectSearchRoots()) {
                Path direct = root.resolve(jarName);
                if (Files.isRegularFile(direct)) {
                    return new PathMatch(direct.toAbsolutePath().normalize(), "文件名匹配: " + jarName);
                }
                Path targetJar = root.resolve("backend/target/" + jarName);
                if (Files.isRegularFile(targetJar)) {
                    return new PathMatch(targetJar.toAbsolutePath().normalize(), "文件名匹配: backend/target/" + jarName);
                }
                Optional<Path> found = findFileByName(root, jarName, pathSearchDepth);
                if (found.isPresent()) {
                    return new PathMatch(found.get(), "递归匹配文件名: " + jarName);
                }
            }
        }
        String prefix = backendJarPrefix != null ? backendJarPrefix.trim() : "backend-0.0.1-SNAPSHOT";
        if (!prefix.isEmpty()) {
            for (Path root : collectSearchRoots()) {
                Optional<Path> found = findJarByPrefix(root, prefix, pathSearchDepth);
                if (found.isPresent()) {
                    return new PathMatch(found.get(), "前缀匹配: " + prefix + "*.jar");
                }
            }
        }
        return null;
    }

    /**
     * 从当前 Java 进程解析 JAR 路径（与宝塔 java -jar /path/to.jar 启动方式一致）。
     * 优先级：ApplicationHome → /proc/self/cmdline 的 -jar → sun.java.command
     */
    private PathMatch resolveRunningJarPath() {
        PathMatch fromHome = resolveJarFromApplicationHome();
        if (fromHome != null) {
            return fromHome;
        }
        PathMatch fromProc = resolveJarFromProcCmdline();
        if (fromProc != null) {
            return fromProc;
        }
        return resolveJarFromSunJavaCommand();
    }

    private PathMatch resolveJarFromApplicationHome() {
        try {
            ApplicationHome home = new ApplicationHome(getClass());
            if (home.getSource() != null && home.getSource().isFile()) {
                Path p = home.getSource().toPath().toAbsolutePath().normalize();
                if (Files.isRegularFile(p)) {
                    return new PathMatch(p, "当前进程 JAR（ApplicationHome）");
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Linux：读取 java -jar /www/wwwroot/backend/backend-0.0.1-SNAPSHOT.jar 中的路径 */
    private PathMatch resolveJarFromProcCmdline() {
        Path proc = Path.of("/proc/self/cmdline");
        if (!Files.isRegularFile(proc)) {
            return null;
        }
        try {
            byte[] bytes = Files.readAllBytes(proc);
            if (bytes.length == 0) {
                return null;
            }
            String[] tokens = new String(bytes, StandardCharsets.UTF_8).split("\0");
            for (int i = 0; i < tokens.length - 1; i++) {
                if (!"-jar".equals(tokens[i])) {
                    continue;
                }
                for (int j = i + 1; j < tokens.length; j++) {
                    String token = tokens[j] != null ? tokens[j].trim() : "";
                    if (token.isEmpty() || token.startsWith("-")) {
                        continue;
                    }
                    if (!token.toLowerCase().endsWith(".jar")) {
                        continue;
                    }
                    Path p = Path.of(token).toAbsolutePath().normalize();
                    if (Files.isRegularFile(p)) {
                        return new PathMatch(p, "Java 启动命令 -jar 参数");
                    }
                }
            }
        } catch (IOException ignored) {
        }
        return null;
    }

    private PathMatch resolveJarFromSunJavaCommand() {
        String cmd = System.getProperty("sun.java.command");
        if (cmd == null || cmd.isBlank()) {
            return null;
        }
        for (String part : cmd.split("\\s+")) {
            if (part == null || part.isBlank() || !part.toLowerCase().endsWith(".jar")) {
                continue;
            }
            Path p = Path.of(part).toAbsolutePath().normalize();
            if (Files.isRegularFile(p)) {
                return new PathMatch(p, "sun.java.command");
            }
        }
        return null;
    }

    private PathMatch resolveFrontendDistMatch() {
        if (configuredDistPath != null && !configuredDistPath.isBlank()) {
            Path p = Path.of(configuredDistPath).toAbsolutePath().normalize();
            return new PathMatch(p, "配置项 xxgkami.update.frontend-dist-path");
        }
        PathMatch deployMatch = findFrontendDistUnderRoots(preferredDeployRoots(), "宝塔部署目录 /www/wwwroot");
        if (deployMatch != null) {
            return deployMatch;
        }
        String dirName = frontendDistDirName.trim();
        for (Path candidate : List.of(
                Path.of(System.getProperty("user.dir")).resolve(dirName),
                Path.of(System.getProperty("user.dir")).resolve("../" + dirName),
                Path.of(System.getProperty("user.dir")).getParent() != null
                        ? Path.of(System.getProperty("user.dir")).getParent().resolve(dirName) : null
        )) {
            if (candidate != null && isValidDistDir(candidate.toAbsolutePath().normalize())) {
                return new PathMatch(candidate.toAbsolutePath().normalize(), "目录名匹配: " + dirName);
            }
        }
        try {
            ApplicationHome home = new ApplicationHome(getClass());
            if (home.getDir() != null) {
                Path beside = home.getDir().toPath().resolve(dirName).toAbsolutePath().normalize();
                if (isValidDistDir(beside)) {
                    return new PathMatch(beside, "JAR 同级目录名: " + dirName);
                }
            }
        } catch (Exception ignored) {
        }
        for (Path root : collectSearchRoots()) {
            Optional<Path> found = findDirByName(root, dirName, pathSearchDepth);
            if (found.isPresent()) {
                return new PathMatch(found.get(), "递归匹配目录名: " + dirName);
            }
        }
        return null;
    }

    /**
     * 宝塔常见站点根：/www/wwwroot 及其下一级子目录（各站点目录）。
     */
    private List<Path> preferredDeployRoots() {
        List<Path> list = new ArrayList<>();
        if (deployRoots == null || deployRoots.isBlank()) {
            return list;
        }
        for (String raw : deployRoots.split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path base = Path.of(trimmed).toAbsolutePath().normalize();
            if (!isSafeSearchRoot(base)) {
                continue;
            }
            list.add(base);
            try (Stream<Path> children = Files.list(base)) {
                children.filter(Files::isDirectory)
                        .map(p -> p.toAbsolutePath().normalize())
                        .filter(this::isSafeSearchRoot)
                        .forEach(list::add);
            } catch (IOException ignored) {
            }
        }
        return list.stream().distinct().toList();
    }

    private PathMatch findBackendJarUnderRoots(List<Path> roots, String scopeLabel) {
        for (String jarName : backendJarNameList()) {
            for (Path root : roots) {
                Path baotaBackend = root.resolve("backend").resolve(jarName);
                if (Files.isRegularFile(baotaBackend)) {
                    return new PathMatch(baotaBackend.toAbsolutePath().normalize(),
                            scopeLabel + " · backend/" + jarName);
                }
                Path direct = root.resolve(jarName);
                if (Files.isRegularFile(direct)) {
                    return new PathMatch(direct.toAbsolutePath().normalize(),
                            scopeLabel + " · 文件名: " + jarName);
                }
                Path nested = root.resolve("backend/target/" + jarName);
                if (Files.isRegularFile(nested)) {
                    return new PathMatch(nested.toAbsolutePath().normalize(),
                            scopeLabel + " · backend/target/" + jarName);
                }
                Optional<Path> found = findFileByName(root, jarName, pathSearchDepth);
                if (found.isPresent()) {
                    return new PathMatch(found.get(), scopeLabel + " · 递归匹配: " + jarName);
                }
            }
        }
        String prefix = backendJarPrefix != null ? backendJarPrefix.trim() : "backend-0.0.1-SNAPSHOT";
        if (!prefix.isEmpty()) {
            for (Path root : roots) {
                Optional<Path> found = findJarByPrefix(root, prefix, pathSearchDepth);
                if (found.isPresent()) {
                    return new PathMatch(found.get(), scopeLabel + " · 前缀: " + prefix + "*.jar");
                }
            }
        }
        return null;
    }

    private PathMatch findFrontendDistUnderRoots(List<Path> roots, String scopeLabel) {
        String dirName = frontendDistDirName.trim();
        for (Path root : roots) {
            Path direct = root.resolve(dirName);
            if (isValidDistDir(direct)) {
                return new PathMatch(direct.toAbsolutePath().normalize(),
                        scopeLabel + " · 目录名: " + dirName);
            }
            if (isValidDistDir(root)) {
                return new PathMatch(root, scopeLabel + " · 站点根目录（含 index.html）");
            }
            try (Stream<Path> children = Files.list(root)) {
                List<Path> siteDirs = children.filter(Files::isDirectory)
                        .map(p -> p.toAbsolutePath().normalize())
                        .filter(this::isSafeSearchRoot)
                        .toList();
                for (Path site : siteDirs) {
                    Path siteDist = site.resolve(dirName);
                    if (isValidDistDir(siteDist)) {
                        return new PathMatch(siteDist,
                                scopeLabel + " · " + pathFileName(site) + "/" + dirName);
                    }
                    if (isValidDistDir(site)) {
                        return new PathMatch(site,
                                scopeLabel + " · 站点目录: " + pathFileName(site));
                    }
                }
            } catch (IOException ignored) {
            }
            Optional<Path> found = findDirByName(root, dirName, pathSearchDepth);
            if (found.isPresent()) {
                return new PathMatch(found.get(), scopeLabel + " · 递归匹配: " + dirName);
            }
            Optional<Path> indexRoot = findNearestIndexHtmlDir(root, pathSearchDepth);
            if (indexRoot.isPresent()) {
                return new PathMatch(indexRoot.get(), scopeLabel + " · 发现 index.html 所在目录");
            }
        }
        return null;
    }

    /** JAR 与 dist 常在同一部署目录下，根据已找到的 JAR 推断 dist */
    private PathMatch findFrontendDistNearJar(Path jarPath) {
        Path parent = jarPath.getParent();
        if (parent == null || !Files.isDirectory(parent)) {
            return null;
        }
        String dirName = frontendDistDirName.trim();
        Path siblingDist = parent.resolve(dirName);
        if (isValidDistDir(siblingDist)) {
            return new PathMatch(siblingDist.toAbsolutePath().normalize(),
                    "与 JAR 同级 · " + dirName);
        }
        try (Stream<Path> children = Files.list(parent)) {
            for (Path child : children.filter(Files::isDirectory).toList()) {
                Path childNorm = child.toAbsolutePath().normalize();
                if (!isSafeSearchRoot(childNorm)) {
                    continue;
                }
                if (pathFileName(jarPath).equals(pathFileName(childNorm))) {
                    continue;
                }
                Path childDist = childNorm.resolve(dirName);
                if (isValidDistDir(childDist)) {
                    return new PathMatch(childDist, "与 JAR 同目录 · " + pathFileName(childNorm) + "/" + dirName);
                }
                if (isValidDistDir(childNorm)) {
                    return new PathMatch(childNorm, "与 JAR 同目录 · 站点: " + pathFileName(childNorm));
                }
            }
        } catch (IOException ignored) {
        }
        Path deployRoot = parent.getParent();
        if (deployRoot != null && isSafeSearchRoot(deployRoot)) {
            PathMatch underDeploy = findFrontendDistUnderRoots(
                    List.of(deployRoot), "相对 JAR · " + pathFileName(deployRoot));
            if (underDeploy != null) {
                return underDeploy;
            }
        }
        return null;
    }

    private Optional<Path> findNearestIndexHtmlDir(Path root, int maxDepth) {
        if (!isSafeSearchRoot(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root, maxDepth)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> "index.html".equalsIgnoreCase(p.getFileName().toString()))
                    .map(p -> p.getParent().toAbsolutePath().normalize())
                    .filter(this::isValidDistDir)
                    .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /** 未匹配到 dist 时仅作填写建议，不表示目录已存在 */
    private String suggestDistPath(PathMatch jarMatch) {
        if (jarMatch != null && jarMatch.path().getParent() != null) {
            return jarMatch.path().getParent().resolve(frontendDistDirName.trim()).toAbsolutePath().normalize().toString();
        }
        for (Path root : preferredDeployRoots()) {
            Path guess = root.resolve(frontendDistDirName.trim());
            return guess.toAbsolutePath().normalize().toString();
        }
        return Path.of(System.getProperty("user.dir")).resolve(frontendDistDirName.trim()).toAbsolutePath().normalize().toString();
    }

    private List<Path> collectSearchRoots() {
        List<Path> roots = new ArrayList<>(preferredDeployRoots());
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        roots.add(cwd);
        if (cwd.getParent() != null) {
            roots.add(cwd.getParent());
        }
        if (cwd.getParent() != null && cwd.getParent().getParent() != null) {
            roots.add(cwd.getParent().getParent());
        }
        try {
            ApplicationHome home = new ApplicationHome(getClass());
            if (home.getDir() != null) {
                roots.add(0, home.getDir().toPath().toAbsolutePath().normalize());
            }
        } catch (Exception ignored) {
        }
        return roots.stream()
                .filter(this::isSafeSearchRoot)
                .distinct()
                .toList();
    }

    /** 跳过盘符根路径等 getFileName() 为 null 的目录，避免 Files.walk 时 NPE */
    private boolean isSafeSearchRoot(Path root) {
        return root != null && Files.isDirectory(root) && root.getFileName() != null;
    }

    private List<String> backendJarNameList() {
        return Arrays.stream(backendJarNames.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private String primaryBackendJarName() {
        List<String> names = backendJarNameList();
        return names.isEmpty() ? "backend-0.0.1-SNAPSHOT.jar" : names.get(0);
    }

    private String defaultJarGuess() {
        PathMatch running = resolveRunningJarPath();
        if (running != null) {
            return running.path().toString();
        }
        PathMatch m = findBackendJarUnderRoots(preferredDeployRoots(), "/www/wwwroot");
        if (m != null) {
            return m.path().toString();
        }
        return Path.of(System.getProperty("user.dir")).resolve(primaryBackendJarName()).toAbsolutePath().normalize().toString();
    }

    private String defaultDistGuess() {
        PathMatch m = findFrontendDistUnderRoots(preferredDeployRoots(), "/www/wwwroot");
        if (m != null) {
            return m.path().toString();
        }
        PathMatch jar = findBackendJarUnderRoots(preferredDeployRoots(), "/www/wwwroot");
        return suggestDistPath(jar);
    }

    private Optional<Path> findFileByName(Path root, String fileName, int maxDepth) {
        if (!isSafeSearchRoot(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root, maxDepth)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> fileName.equalsIgnoreCase(p.getFileName().toString()))
                    .map(p -> p.toAbsolutePath().normalize())
                    .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Path> findJarByPrefix(Path root, String prefix, int maxDepth) {
        if (!isSafeSearchRoot(root)) {
            return Optional.empty();
        }
        String pfx = prefix.toLowerCase();
        try (Stream<Path> walk = Files.walk(root, maxDepth)) {
            return walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        return n.startsWith(pfx) && n.endsWith(".jar");
                    })
                    .map(p -> p.toAbsolutePath().normalize())
                    .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private Optional<Path> findDirByName(Path root, String dirName, int maxDepth) {
        if (!isSafeSearchRoot(root)) {
            return Optional.empty();
        }
        try (Stream<Path> walk = Files.walk(root, maxDepth)) {
            return walk.filter(Files::isDirectory)
                    .filter(p -> p.getFileName() != null)
                    .filter(p -> dirName.equalsIgnoreCase(p.getFileName().toString()))
                    .filter(this::isValidDistDir)
                    .map(p -> p.toAbsolutePath().normalize())
                    .findFirst();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    private boolean isValidDistDir(Path dist) {
        return Files.isDirectory(dist) && Files.isRegularFile(dist.resolve("index.html"));
    }

    private static String pathFileName(Path path) {
        if (path == null) {
            return "";
        }
        Path name = path.getFileName();
        return name != null ? name.toString() : path.toString();
    }

    private static String pathFileNameOr(Path path, String fallback) {
        if (path == null) {
            return fallback;
        }
        Path name = path.getFileName();
        return name != null ? name.toString() : fallback;
    }

    private void deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void updateStatus(String st, int percent, String message, String phase, String channel, String remoteVersion) {
        updateStatus(st, percent, message, phase, channel, remoteVersion, null);
    }

    private void updateStatus(String st, int percent, String message, String phase,
                              String channel, String remoteVersion, String downloadUrl) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", st);
        m.put("percent", percent);
        m.put("message", message);
        m.put("phase", phase);
        m.put("channel", channel);
        m.put("remoteVersion", remoteVersion);
        if (downloadUrl != null && !downloadUrl.isBlank()) {
            m.put("downloadUrl", downloadUrl);
        }
        status.set(m);
    }

    private static Map<String, Object> idleStatus() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", "idle");
        m.put("percent", 0);
        m.put("message", "空闲");
        m.put("phase", "idle");
        return m;
    }
}
