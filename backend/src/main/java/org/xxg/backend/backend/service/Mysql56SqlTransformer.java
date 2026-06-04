package org.xxg.backend.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 MySQL 8.0 导出的 kami.sql 转为 5.6 / MariaDB 可执行的 DDL/DML。
 */
public final class Mysql56SqlTransformer {

    private static final Pattern COLLATION_0900 = Pattern.compile("utf8mb4_0900_[a-z_]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CARD_HASH_VARCHAR = Pattern.compile("(`card_hash`\\s+varchar\\()255(\\))", Pattern.CASE_INSENSITIVE);

    private Mysql56SqlTransformer() {
    }

    public record TransformResult(
            String sql,
            int collationReplacements,
            int cardHashColumnFixes,
            int uniqueIndexPrefixFixes
    ) {
    }

    /**
     * @param onPercent 0–100，用于转译进度
     */
    public static TransformResult transform(String source, IntConsumer onPercent) {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("种子 SQL 为空");
        }
        onPercent.accept(5);

        String sql = source;
        List<String> header = new ArrayList<>();
        header.add("/* Auto-translated from kami.sql for MySQL 5.6+ / MariaDB — do not edit manually */");

        onPercent.accept(15);
        Matcher collationMatcher = COLLATION_0900.matcher(sql);
        int collationCount = 0;
        StringBuilder collationBuf = new StringBuilder();
        while (collationMatcher.find()) {
            collationCount++;
        }
        if (collationCount > 0) {
            sql = COLLATION_0900.matcher(sql).replaceAll("utf8mb4_general_ci");
        }
        onPercent.accept(40);

        Matcher cardHashMatcher = CARD_HASH_VARCHAR.matcher(sql);
        StringBuilder cardHashBuf = new StringBuilder();
        int cardHashFixes = 0;
        while (cardHashMatcher.find()) {
            cardHashFixes++;
            cardHashMatcher.appendReplacement(
                    cardHashBuf,
                    Matcher.quoteReplacement(cardHashMatcher.group(1) + "191" + cardHashMatcher.group(2)));
        }
        if (cardHashFixes > 0) {
            cardHashMatcher.appendTail(cardHashBuf);
            sql = cardHashBuf.toString();
        }
        onPercent.accept(55);

        int indexFixes = 0;
        String[][] indexRules = {
                {"UNIQUE INDEX `idx_api_key_value`(`key_value` ASC)", "UNIQUE INDEX `idx_api_key_value`(`key_value`(191) ASC)"},
                {"UNIQUE INDEX `card_key`(`card_key` ASC)", "UNIQUE INDEX `card_key`(`card_key`(191) ASC)"},
                {"UNIQUE INDEX `encrypted_key`(`encrypted_key` ASC)", "UNIQUE INDEX `encrypted_key`(`encrypted_key`(191) ASC)"},
        };
        for (String[] rule : indexRules) {
            if (sql.contains(rule[0]) && !sql.contains(rule[1])) {
                sql = sql.replace(rule[0], rule[1]);
                indexFixes++;
            }
        }
        onPercent.accept(70);

        sql = sql.replace("ROW_FORMAT = Dynamic", "ROW_FORMAT = DYNAMIC");
        sql = sql.replace("ROW_FORMAT=dynamic", "ROW_FORMAT=DYNAMIC");

        onPercent.accept(85);
        String body = String.join(System.lineSeparator(), header) + System.lineSeparator() + sql;
        onPercent.accept(95);
        return new TransformResult(body, collationCount, cardHashFixes, indexFixes);
    }
}
