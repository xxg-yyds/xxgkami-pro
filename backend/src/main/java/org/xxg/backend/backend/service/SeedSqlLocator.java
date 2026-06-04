package org.xxg.backend.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

/**
 * 定位种子 SQL：优先磁盘路径，否则从 JAR 内 classpath 解压到 data 目录供 mysql 导入。
 */
@Service
public class SeedSqlLocator {

    private static final String[] KAMI_FS_PATHS = {
            "databaes/kami.sql",
            "database/kami.sql",
            "../databaes/kami.sql",
            "xxgkami-vue/databaes/kami.sql"
    };

    private static final String KAMI_CLASSPATH = "databaes/kami.sql";

    @Value("${xxgkami.setup.seed-sql-override:}")
    private String seedSqlOverride;

    @Value("${xxgkami.setup.seed-extract-dir:data/.xxgkami-seed}")
    private String seedExtractDir;

    public Optional<Path> resolveKamiSql() throws IOException {
        if (seedSqlOverride != null && !seedSqlOverride.isBlank()) {
            Path custom = Path.of(seedSqlOverride).toAbsolutePath().normalize();
            if (Files.isRegularFile(custom)) {
                return Optional.of(custom);
            }
        }
        Optional<Path> onDisk = findOnFilesystem(KAMI_FS_PATHS);
        if (onDisk.isPresent()) {
            return onDisk;
        }
        return materializeClasspath(KAMI_CLASSPATH, "kami.sql");
    }

    public Optional<Path> resolveSimpleCardsSql() throws IOException {
        String[] paths = {"databaes/simple_cards.sql", "database/simple_cards.sql"};
        Optional<Path> onDisk = findOnFilesystem(paths);
        if (onDisk.isPresent()) {
            return onDisk;
        }
        Resource resource = new ClassPathResource("databaes/simple_cards.sql");
        if (resource.exists()) {
            return materializeClasspath("databaes/simple_cards.sql", "simple_cards.sql");
        }
        return Optional.empty();
    }

    public boolean isBundledInJar() {
        return new ClassPathResource(KAMI_CLASSPATH).exists();
    }

    private Optional<Path> findOnFilesystem(String... relatives) {
        Path cwd = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path root : new Path[]{cwd, cwd.getParent(), cwd.resolve("..").normalize()}) {
            if (root == null) {
                continue;
            }
            for (String rel : relatives) {
                Path p = root.resolve(rel).normalize();
                if (Files.isRegularFile(p)) {
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Path> materializeClasspath(String classpath, String fileName) throws IOException {
        Resource resource = new ClassPathResource(classpath);
        if (!resource.exists()) {
            return Optional.empty();
        }
        Path dir = Path.of(seedExtractDir).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(fileName);
        long srcModified = resource.lastModified();
        if (Files.isRegularFile(target) && srcModified > 0
                && Files.getLastModifiedTime(target).toMillis() >= srcModified) {
            return Optional.of(target);
        }
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return Optional.of(target);
    }
}
