package org.xxg.backend.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 根据 Gitee / GitHub 站点可达性选择版本检查通道。
 */
@Service
public class RemoteUpdateService {

    public enum Channel {
        GITEE,
        GITHUB
    }

    private static final String GITEE_PING = "https://gitee.com/";
    private static final String GITHUB_PING = "https://github.com/";

    @Value("${xxgkami.update.gitee-version-json:https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/raw/master/public/version.json}")
    private String giteeVersionJsonUrl;

    @Value("${xxgkami.update.github-version-json:https://raw.githubusercontent.com/xxg-yyds/xxgkami-pro/refs/heads/master/public/version.json}")
    private String githubVersionJsonUrl;

    private static final int PING_CONNECT_MS = 3500;
    private static final int PING_READ_MS = 3500;
    private static final int VERSION_CONNECT_MS = 5000;
    private static final int VERSION_READ_MS = 8000;
    private static final long PING_RACE_MS = 6000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 并行 ping 两个站点，先成功返回的一方作为首选通道。
     */
    public Channel detectPreferredChannel() {
        AtomicReference<Channel> winner = new AtomicReference<>();
        CompletableFuture<Void> gitee = CompletableFuture.runAsync(() -> {
            if (pingSite(GITEE_PING)) {
                winner.compareAndSet(null, Channel.GITEE);
            }
        });
        CompletableFuture<Void> github = CompletableFuture.runAsync(() -> {
            if (pingSite(GITHUB_PING)) {
                winner.compareAndSet(null, Channel.GITHUB);
            }
        });

        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PING_RACE_MS);
        while (System.nanoTime() < deadline && winner.get() == null) {
            if (gitee.isDone() && github.isDone()) {
                break;
            }
            try {
                Thread.sleep(40);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        Channel chosen = winner.get();
        if (chosen != null) {
            return chosen;
        }
        if (pingSite(GITEE_PING)) {
            return Channel.GITEE;
        }
        if (pingSite(GITHUB_PING)) {
            return Channel.GITHUB;
        }
        return Channel.GITEE;
    }

    public String versionJsonUrl(Channel channel) {
        return channel == Channel.GITEE ? giteeVersionJsonUrl : githubVersionJsonUrl;
    }

    /** 拉取到的 version.json 及其来源通道（用于响应头与下载通道解析） */
    public record FetchedVersion(String json, Channel jsonSource) {
    }

    /**
     * 拉取远程 version.json：优先使用含 releaseDownloads 的源；
     * Gitee 未同步时自动回退到 GitHub raw 地址。
     */
    public Optional<FetchedVersion> fetchBestRemoteVersionJson() {
        Channel preferred = detectPreferredChannel();
        Channel alt = preferred == Channel.GITEE ? Channel.GITHUB : Channel.GITEE;
        Optional<String> preferredJson = fetchJson(versionJsonUrl(preferred));
        Optional<String> altJson = fetchJson(versionJsonUrl(alt));

        if (preferredJson.isPresent() && hasReleaseDownloads(preferredJson.get())) {
            return Optional.of(new FetchedVersion(preferredJson.get(), preferred));
        }
        if (altJson.isPresent() && hasReleaseDownloads(altJson.get())) {
            return Optional.of(new FetchedVersion(altJson.get(), alt));
        }
        if (preferredJson.isPresent()) {
            return Optional.of(new FetchedVersion(preferredJson.get(), preferred));
        }
        return altJson.map(j -> new FetchedVersion(j, alt));
    }

    /**
     * 拉取远程 version.json 全文（兼容旧调用，内部走 fetchBestRemoteVersionJson）。
     */
    public Optional<String> fetchRemoteVersionJson() {
        return fetchBestRemoteVersionJson().map(FetchedVersion::json);
    }

    public Optional<String> fetchRemoteVersionJson(Channel primary) {
        return fetchBestRemoteVersionJson()
                .map(FetchedVersion::json)
                .or(() -> fetchJson(versionJsonUrl(primary)))
                .or(() -> fetchJson(versionJsonUrl(primary == Channel.GITEE ? Channel.GITHUB : Channel.GITEE)));
    }

    public Optional<String> fetchRemoteVersion() {
        return fetchBestRemoteVersionJson()
                .flatMap(f -> parseVersion(f.json()));
    }

    public record ReleaseArtifacts(Channel channel, String versionTag, String distZipUrl, String backendJarUrl) {
        ReleaseArtifacts(String distZipUrl, String backendJarUrl) {
            this(null, null, distZipUrl, backendJarUrl);
        }
    }

    /**
     * 解析 Release 下载地址：先读 JSON 内对应通道，再尝试另一通道，最后用内置模板兜底。
     */
    public Optional<ReleaseArtifacts> resolveReleaseArtifacts(String json, Channel jsonSource) {
        List<ReleaseArtifacts> plans = resolveDownloadPlans(json);
        if (plans.isEmpty()) {
            return Optional.empty();
        }
        Channel preferred = detectPreferredChannel();
        return plans.stream()
                .filter(p -> p.channel() == preferred)
                .findFirst()
                .or(() -> plans.stream().findFirst());
    }

    /**
     * 按「下载通道优先（国内 Gitee）→ 版本 tag 变体（1.0.7 / v1.0.7）」生成下载计划列表，供失败时依次重试。
     */
    public List<ReleaseArtifacts> resolveDownloadPlans(String json) {
        Optional<String> versionOpt = parseVersion(json);
        if (versionOpt.isEmpty()) {
            return List.of();
        }
        List<String> tags = versionTagVariants(versionOpt.get());
        Channel preferred = detectPreferredChannel();
        List<Channel> channelOrder = preferred == Channel.GITEE
                ? List.of(Channel.GITEE, Channel.GITHUB)
                : List.of(Channel.GITHUB, Channel.GITEE);

        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<ReleaseArtifacts> plans = new ArrayList<>();
        for (Channel ch : channelOrder) {
            for (String tag : tags) {
                parseReleaseArtifacts(json, ch, tag).ifPresent(a -> {
                    String key = a.backendJarUrl() + "|" + a.distZipUrl();
                    if (seen.add(key)) {
                        plans.add(a);
                    }
                });
                builtInReleaseArtifacts(ch, tag).ifPresent(a -> {
                    String key = a.backendJarUrl() + "|" + a.distZipUrl();
                    if (seen.add(key)) {
                        plans.add(a);
                    }
                });
            }
        }
        return plans;
    }

    private List<String> versionTagVariants(String version) {
        List<String> tags = new ArrayList<>();
        if (version == null || version.isBlank()) {
            return tags;
        }
        String v = version.trim();
        tags.add(v);
        if (!v.startsWith("v")) {
            tags.add("v" + v);
        }
        if (v.startsWith("v") && v.length() > 1) {
            tags.add(v.substring(1));
        }
        return tags.stream().distinct().toList();
    }

    private Optional<ReleaseArtifacts> builtInReleaseArtifacts(Channel channel, String version) {
        if (version == null || version.isBlank()) {
            return Optional.empty();
        }
        if (channel == Channel.GITEE) {
            String base = "https://gitee.com/xiaoxiaoguai-yyds/xxgkami-pro/releases/download/" + version + "/";
            return Optional.of(new ReleaseArtifacts(Channel.GITEE, version, base + "dist.zip", base + "backend-0.0.1-SNAPSHOT.jar"));
        }
        String base = "https://github.com/xxg-yyds/xxgkami-pro/releases/download/" + version + "/";
        return Optional.of(new ReleaseArtifacts(Channel.GITHUB, version, base + "dist.zip", base + "backend-0.0.1-SNAPSHOT.jar"));
    }

    private boolean hasReleaseDownloads(String json) {
        if (json == null || json.isBlank()) {
            return false;
        }
        try {
            JsonNode downloads = objectMapper.readTree(json).get("releaseDownloads");
            return downloads != null && !downloads.isMissingNode() && downloads.size() > 0;
        } catch (Exception ignored) {
            return false;
        }
    }

    /**
     * 从 version.json 的 releaseDownloads 解析指定通道的下载地址。
     */
    public Optional<ReleaseArtifacts> parseReleaseArtifacts(String json, Channel channel) {
        return parseReleaseArtifacts(json, channel, null);
    }

    public Optional<ReleaseArtifacts> parseReleaseArtifacts(String json, Channel channel, String versionTag) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            String version = versionTag != null && !versionTag.isBlank()
                    ? versionTag.trim()
                    : (root.has("version") ? root.get("version").asText().trim() : "");
            if (version.isEmpty()) {
                return Optional.empty();
            }
            JsonNode downloads = root.get("releaseDownloads");
            if (downloads == null || downloads.isMissingNode()) {
                return Optional.empty();
            }
            String key = channel == Channel.GITEE ? "gitee" : "github";
            JsonNode node = downloads.get(key);
            if (node == null || node.isMissingNode()) {
                return Optional.empty();
            }
            String dist = textOrTemplate(node, "distZip", version);
            String jar = textOrTemplate(node, "backendJar", version);
            if (dist == null || jar == null) {
                return Optional.empty();
            }
            return Optional.of(new ReleaseArtifacts(channel, version, dist, jar));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String textOrTemplate(JsonNode node, String field, String version) {
        JsonNode n = node.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        String raw = n.asText().trim();
        if (raw.isEmpty()) {
            return null;
        }
        return raw.replace("{version}", version);
    }

    public Optional<String> parseVersion(String json) {
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode v = root.get("version");
            if (v != null && !v.asText().isBlank()) {
                return Optional.of(v.asText().trim());
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private Optional<String> fetchJson(String url) {
        try {
            String body = versionRestTemplate().getForObject(url, String.class);
            if (body != null && !body.isBlank()) {
                return Optional.of(body);
            }
        } catch (Exception ignored) {
        }
        return Optional.empty();
    }

    private boolean pingSite(String url) {
        RestTemplate rest = pingRestTemplate();
        try {
            rest.exchange(URI.create(url), HttpMethod.HEAD, null, Void.class);
            return true;
        } catch (Exception headFailed) {
            try {
                rest.getForEntity(url, String.class);
                return true;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private RestTemplate pingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(PING_CONNECT_MS);
        factory.setReadTimeout(PING_READ_MS);
        return new RestTemplate(factory);
    }

    private RestTemplate versionRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(VERSION_CONNECT_MS);
        factory.setReadTimeout(VERSION_READ_MS);
        return new RestTemplate(factory);
    }
}
