package org.xxg.backend.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 记录已完成「新版完善度检测/升级确认」的应用版本。
 */
@Service
public class SetupVersionService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${xxgkami.setup.version-file:data/.xxgkami-version.json}")
    private String versionFilePath;

    public boolean hasRecordedVersion() {
        return Files.isRegularFile(resolvePath());
    }

    public Path resolvePath() {
        return Path.of(versionFilePath).toAbsolutePath().normalize();
    }

    public String readRecordedVersion() throws IOException {
        Path path = resolvePath();
        if (!Files.isRegularFile(path)) {
            return null;
        }
        JsonNode root = MAPPER.readTree(Files.readString(path));
        JsonNode v = root.get("version");
        return v != null && !v.asText().isBlank() ? v.asText().trim() : null;
    }

    public void writeRecordedVersion(String version) throws IOException {
        Path path = resolvePath();
        Files.createDirectories(path.getParent());
        Map<String, Object> body = new HashMap<>();
        body.put("version", version);
        body.put("recordedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(body));
    }

    /** @return 负数表示 a&lt;b，0 相等，正数 a&gt;b */
    public int compareVersions(String a, String b) {
        if (a == null || a.isBlank()) {
            return b == null || b.isBlank() ? 0 : -1;
        }
        if (b == null || b.isBlank()) {
            return 1;
        }
        String[] pa = a.trim().split("\\.");
        String[] pb = b.trim().split("\\.");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            int na = i < pa.length ? parseVersionPart(pa[i]) : 0;
            int nb = i < pb.length ? parseVersionPart(pb[i]) : 0;
            if (na != nb) {
                return Integer.compare(na, nb);
            }
        }
        return 0;
    }

    private int parseVersionPart(String part) {
        String digits = part.replaceAll("[^0-9].*", "");
        if (digits.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public Optional<String> readLocalAppVersion() {
        for (Path p : localVersionCandidates()) {
            if (!Files.isRegularFile(p)) {
                continue;
            }
            try {
                JsonNode root = MAPPER.readTree(Files.readString(p));
                JsonNode v = root.get("version");
                if (v != null && !v.asText().isBlank()) {
                    return Optional.of(v.asText().trim());
                }
            } catch (Exception ignored) {
            }
        }
        return Optional.empty();
    }

    private java.util.List<Path> localVersionCandidates() {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return java.util.List.of(
                cwd.resolve("public/version.json"),
                cwd.resolve("../public/version.json"),
                cwd.resolve("../../public/version.json")
        );
    }
}
