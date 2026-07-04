package org.xxg.backend.backend.mapper;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.xxg.backend.backend.entity.Card;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 简单（未加密）卡密数据访问
 */
@Repository
public class SimpleCardMapper {

    private final JdbcTemplate jdbcTemplate;

    public SimpleCardMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureTableExists();
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS simple_cards (" +
                            "id INT NOT NULL AUTO_INCREMENT," +
                            "card_key VARCHAR(128) NOT NULL," +
                            "status TINYINT(1) NOT NULL DEFAULT 0," +
                            "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "use_time DATETIME NULL," +
                            "expire_time DATETIME NULL," +
                            "duration INT NOT NULL DEFAULT 0," +
                            "verify_method ENUM('web','post','get') DEFAULT 'web'," +
                            "allow_reverify TINYINT(1) NOT NULL DEFAULT 1," +
                            "device_id VARCHAR(64) NULL," +
                            "card_type ENUM('time','count') NOT NULL DEFAULT 'time'," +
                            "total_count INT NOT NULL DEFAULT 0," +
                            "remaining_count INT NOT NULL DEFAULT 0," +
                            "creator_type ENUM('admin','user') NOT NULL DEFAULT 'admin'," +
                            "creator_id INT NOT NULL," +
                            "creator_name VARCHAR(50) NOT NULL," +
                            "ip_address VARCHAR(255) NULL," +
                            "api_key_id BIGINT NULL," +
                            "machine_code VARCHAR(255) NULL," +
                            "stack_time_if_same_machine TINYINT(1) NOT NULL DEFAULT 0," +
                            "allow_self_unbind TINYINT(1) NOT NULL DEFAULT 0," +
                            "merged_into_card_id BIGINT NULL," +
                            "PRIMARY KEY (id)," +
                            "UNIQUE KEY uk_simple_card_key (card_key)," +
                            "INDEX idx_simple_machine_code (machine_code)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        } catch (Exception e) {
            System.err.println("simple_cards table init: " + e.getMessage());
        }
        try {
            jdbcTemplate.execute("ALTER TABLE simple_cards ADD COLUMN duration_unit VARCHAR(10) NOT NULL DEFAULT 'days' COMMENT 'time card unit: days or hours'");
        } catch (Exception e) {
            // exists
        }
        try {
            jdbcTemplate.execute("ALTER TABLE simple_cards ADD COLUMN require_device_unbind TINYINT(1) NOT NULL DEFAULT 0 COMMENT '自助解绑时须验证原设备码'");
        } catch (Exception e) {
            // exists
        }
        try {
            jdbcTemplate.execute("ALTER TABLE simple_cards ADD COLUMN unbind_cooldown_hours INT NOT NULL DEFAULT 0 COMMENT '自助解绑冷却间隔(小时)，0=不限'");
        } catch (Exception e) {
            // exists
        }
        try {
            jdbcTemplate.execute("ALTER TABLE simple_cards ADD COLUMN unbind_max_count INT NOT NULL DEFAULT 0 COMMENT '自助解绑次数上限，0=不限'");
        } catch (Exception e) {
            // exists
        }
        try {
            jdbcTemplate.execute("ALTER TABLE simple_cards ADD COLUMN unbind_count INT NOT NULL DEFAULT 0 COMMENT '已累计自助解绑次数'");
        } catch (Exception e) {
            // exists
        }
        try {
            jdbcTemplate.execute("ALTER TABLE simple_cards ADD COLUMN last_unbind_time DATETIME NULL COMMENT '最近一次自助解绑时间'");
        } catch (Exception e) {
            // exists
        }
    }

    public Card findByCardKey(String cardKey) {
        String sql = "SELECT * FROM simple_cards WHERE card_key = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new SimpleCardRowMapper(), cardKey);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    public Card findById(Long id) {
        String sql = "SELECT * FROM simple_cards WHERE id = ?";
        try {
            return jdbcTemplate.queryForObject(sql, new SimpleCardRowMapper(), id);
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            return null;
        }
    }

    public boolean existsByCardKey(String cardKey) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simple_cards WHERE card_key = ?",
                Integer.class,
                cardKey
        );
        return n != null && n > 0;
    }

    /**
     * 查找指定前缀 + 固定总长度下，纯数字后缀的最大值（用于递增生成）。
     */
    public long maxNumericSuffixForPrefix(String prefix, int totalLength) {
        String p = prefix == null ? "" : prefix;
        List<String> keys;
        if (p.isEmpty()) {
            keys = jdbcTemplate.queryForList(
                    "SELECT card_key FROM simple_cards WHERE LENGTH(card_key) = ? AND card_key REGEXP '^[0-9]+$'",
                    String.class,
                    totalLength
            );
        } else {
            keys = jdbcTemplate.queryForList(
                    "SELECT card_key FROM simple_cards WHERE card_key LIKE ? AND LENGTH(card_key) = ?",
                    String.class,
                    p + "%",
                    totalLength
            );
        }
        long max = 0;
        for (String key : keys) {
            if (key == null || !key.startsWith(p)) {
                continue;
            }
            String suffix = key.substring(p.length());
            if (!suffix.matches("\\d+")) {
                continue;
            }
            try {
                max = Math.max(max, Long.parseLong(suffix));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return max;
    }

    public List<Card> findAll() {
        return jdbcTemplate.query("SELECT * FROM simple_cards ORDER BY create_time DESC", new SimpleCardRowMapper());
    }

    public List<Card> findByApiKeyId(Long apiKeyId) {
        return jdbcTemplate.query(
                "SELECT * FROM simple_cards WHERE api_key_id = ? ORDER BY create_time DESC",
                new SimpleCardRowMapper(),
                apiKeyId
        );
    }

    public int countTotalCards() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM simple_cards", Integer.class);
        return count != null ? count : 0;
    }

    public int countUsedCards() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simple_cards WHERE status IN (1, 4)",
                Integer.class
        );
        return count != null ? count : 0;
    }

    public int countActiveCards() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simple_cards WHERE status = 0",
                Integer.class
        );
        return count != null ? count : 0;
    }

    public int countByApiKeyId(Long apiKeyId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simple_cards WHERE api_key_id = ?",
                Integer.class,
                apiKeyId
        );
        return count != null ? count : 0;
    }

    public List<Map<String, Object>> getUsageTrend(int days) {
        String sql = "SELECT DATE(use_time) as date, COUNT(*) as count " +
                "FROM simple_cards " +
                "WHERE use_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                "AND status IN (1, 4) " +
                "GROUP BY DATE(use_time) " +
                "ORDER BY date ASC";
        return queryDailyCounts(sql, days);
    }

    public List<Map<String, Object>> getUnusedCreatedTrend(int days) {
        String sql = "SELECT DATE(create_time) as date, COUNT(*) as count " +
                "FROM simple_cards " +
                "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                "AND status = 0 " +
                "GROUP BY DATE(create_time) " +
                "ORDER BY date ASC";
        return queryDailyCounts(sql, days);
    }

    public List<Map<String, Object>> getExpiredTrend(int days) {
        String sql = "SELECT DATE(expire_time) as date, COUNT(*) as count " +
                "FROM simple_cards " +
                "WHERE expire_time IS NOT NULL " +
                "AND expire_time >= DATE_SUB(CURDATE(), INTERVAL ? DAY) " +
                "AND expire_time <= NOW() " +
                "GROUP BY DATE(expire_time) " +
                "ORDER BY date ASC";
        return queryDailyCounts(sql, days);
    }

    private List<Map<String, Object>> queryDailyCounts(String sql, int days) {
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> map = new java.util.HashMap<>();
            map.put("date", rs.getDate("date").toString());
            map.put("count", rs.getInt("count"));
            return map;
        }, days);
    }

    public List<Card> listStackAnchorCandidates(String machineCode, Long excludeCardId) {
        if (machineCode == null || machineCode.isEmpty()) {
            return new ArrayList<>();
        }
        Long ex = excludeCardId != null ? excludeCardId : -1L;
        String sql = "SELECT * FROM simple_cards WHERE machine_code = ? AND card_type = 'time' AND status = 1 AND id <> ?";
        return jdbcTemplate.query(sql, new SimpleCardRowMapper(), machineCode, ex);
    }

    public void batchInsert(List<Card> cards) {
        String sql = "INSERT INTO simple_cards (card_key, card_type, duration, duration_unit, total_count, remaining_count, status, " +
                "verify_method, allow_reverify, create_time, creator_type, creator_id, creator_name, api_key_id, " +
                "stack_time_if_same_machine, allow_self_unbind, require_device_unbind, unbind_cooldown_hours, unbind_max_count, unbind_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Card card = cards.get(i);
                ps.setString(1, card.getCardKey());
                ps.setString(2, card.getCardType());
                ps.setInt(3, card.getDuration());
                ps.setString(4, normalizeDurationUnitForDb(card.getDurationUnit()));
                ps.setInt(5, card.getTotalCount());
                ps.setInt(6, card.getRemainingCount());
                ps.setInt(7, card.getStatus());
                ps.setString(8, card.getVerifyMethod());
                ps.setInt(9, card.getAllowReverify());
                ps.setTimestamp(10, Timestamp.valueOf(card.getCreateTime()));
                ps.setString(11, card.getCreatorType());
                ps.setLong(12, card.getCreatorId());
                ps.setString(13, card.getCreatorName());
                if (card.getApiKeyId() != null) {
                    ps.setLong(14, card.getApiKeyId());
                } else {
                    ps.setNull(14, java.sql.Types.BIGINT);
                }
                ps.setInt(15, Boolean.TRUE.equals(card.getStackTimeIfSameMachine()) ? 1 : 0);
                ps.setInt(16, Boolean.TRUE.equals(card.getAllowSelfUnbind()) ? 1 : 0);
                ps.setInt(17, Boolean.TRUE.equals(card.getRequireDeviceUnbind()) ? 1 : 0);
                ps.setInt(18, card.getUnbindCooldownHours() != null ? card.getUnbindCooldownHours() : 0);
                ps.setInt(19, card.getUnbindMaxCount() != null ? card.getUnbindMaxCount() : 0);
                ps.setInt(20, card.getUnbindCount() != null ? card.getUnbindCount() : 0);
            }

            @Override
            public int getBatchSize() {
                return cards.size();
            }
        });
    }

    public void update(Card card) {
        jdbcTemplate.update(
                "UPDATE simple_cards SET card_key=?, card_type=?, duration=?, duration_unit=?, total_count=?, remaining_count=?, " +
                        "status=?, verify_method=?, allow_reverify=?, use_time=?, expire_time=?, device_id=?, " +
                        "ip_address=?, machine_code=?, stack_time_if_same_machine=?, allow_self_unbind=?, " +
                        "require_device_unbind=?, unbind_cooldown_hours=?, unbind_max_count=?, unbind_count=?, " +
                        "last_unbind_time=?, merged_into_card_id=?, api_key_id=? WHERE id=?",
                card.getCardKey(),
                card.getCardType(),
                card.getDuration(),
                normalizeDurationUnitForDb(card.getDurationUnit()),
                card.getTotalCount(),
                card.getRemainingCount(),
                card.getStatus(),
                card.getVerifyMethod(),
                card.getAllowReverify(),
                card.getUseTime() != null ? Timestamp.valueOf(card.getUseTime()) : null,
                card.getExpireTime() != null ? Timestamp.valueOf(card.getExpireTime()) : null,
                card.getDeviceId(),
                card.getIpAddress(),
                card.getMachineCode(),
                Boolean.TRUE.equals(card.getStackTimeIfSameMachine()) ? 1 : 0,
                Boolean.TRUE.equals(card.getAllowSelfUnbind()) ? 1 : 0,
                Boolean.TRUE.equals(card.getRequireDeviceUnbind()) ? 1 : 0,
                card.getUnbindCooldownHours() != null ? card.getUnbindCooldownHours() : 0,
                card.getUnbindMaxCount() != null ? card.getUnbindMaxCount() : 0,
                card.getUnbindCount() != null ? card.getUnbindCount() : 0,
                card.getLastUnbindTime() != null ? Timestamp.valueOf(card.getLastUnbindTime()) : null,
                card.getMergedIntoCardId(),
                card.getApiKeyId(),
                card.getId()
        );
    }

    public void updateExpireTimeById(Long cardId, java.time.LocalDateTime expireTime) {
        jdbcTemplate.update(
                "UPDATE simple_cards SET expire_time = ? WHERE id = ?",
                expireTime != null ? Timestamp.valueOf(expireTime) : null,
                cardId
        );
    }

    public void markCardMergedInto(Long consumedCardId, Long anchorCardId, java.time.LocalDateTime useTime,
                                  String machineCode, String deviceId, String ipAddress) {
        jdbcTemplate.update(
                "UPDATE simple_cards SET status = 4, merged_into_card_id = ?, use_time = ?, expire_time = NULL, " +
                        "machine_code = ?, device_id = ?, ip_address = ? WHERE id = ?",
                anchorCardId,
                Timestamp.valueOf(useTime),
                machineCode,
                deviceId,
                ipAddress,
                consumedCardId
        );
    }

    public void updateStatusOnly(Long id, int status) {
        jdbcTemplate.update("UPDATE simple_cards SET status = ? WHERE id = ?", status, id);
    }

    public void delete(Long id) {
        jdbcTemplate.update("DELETE FROM simple_cards WHERE id = ?", id);
    }

    private static class SimpleCardRowMapper implements RowMapper<Card> {
        @Override
        public Card mapRow(ResultSet rs, int rowNum) throws SQLException {
            Card card = new Card();
            card.setId(rs.getLong("id"));
            card.setCardKey(rs.getString("card_key"));
            card.setEncryptedKey(null);
            card.setStatus(rs.getInt("status"));
            Timestamp ct = rs.getTimestamp("create_time");
            if (ct != null) {
                card.setCreateTime(ct.toLocalDateTime());
            }
            Timestamp ut = rs.getTimestamp("use_time");
            if (ut != null) {
                card.setUseTime(ut.toLocalDateTime());
            }
            Timestamp et = rs.getTimestamp("expire_time");
            if (et != null) {
                card.setExpireTime(et.toLocalDateTime());
            }
            card.setDuration(rs.getInt("duration"));
            try {
                card.setDurationUnit(rs.getString("duration_unit"));
            } catch (SQLException ignored) {
                card.setDurationUnit("days");
            }
            card.setVerifyMethod(rs.getString("verify_method"));
            card.setAllowReverify(rs.getInt("allow_reverify"));
            card.setDeviceId(rs.getString("device_id"));
            card.setIpAddress(rs.getString("ip_address"));
            card.setEncryptionType("simple");
            card.setStorageType("simple");
            card.setCardType(rs.getString("card_type"));
            card.setTotalCount(rs.getInt("total_count"));
            card.setRemainingCount(rs.getInt("remaining_count"));
            card.setCreatorType(rs.getString("creator_type"));
            card.setCreatorId(rs.getLong("creator_id"));
            card.setCreatorName(rs.getString("creator_name"));
            long apiKeyId = rs.getLong("api_key_id");
            if (!rs.wasNull()) {
                card.setApiKeyId(apiKeyId);
            }
            card.setMachineCode(rs.getString("machine_code"));
            card.setStackTimeIfSameMachine(rs.getInt("stack_time_if_same_machine") == 1);
            card.setAllowSelfUnbind(rs.getInt("allow_self_unbind") == 1);
            try {
                card.setRequireDeviceUnbind(rs.getInt("require_device_unbind") == 1);
            } catch (SQLException ignored) {
            }
            try {
                card.setUnbindCooldownHours(rs.getInt("unbind_cooldown_hours"));
            } catch (SQLException ignored) {
            }
            try {
                card.setUnbindMaxCount(rs.getInt("unbind_max_count"));
            } catch (SQLException ignored) {
            }
            try {
                card.setUnbindCount(rs.getInt("unbind_count"));
            } catch (SQLException ignored) {
            }
            try {
                if (rs.getTimestamp("last_unbind_time") != null) {
                    card.setLastUnbindTime(rs.getTimestamp("last_unbind_time").toLocalDateTime());
                }
            } catch (SQLException ignored) {
            }
            long merged = rs.getLong("merged_into_card_id");
            if (!rs.wasNull()) {
                card.setMergedIntoCardId(merged);
            }
            return card;
        }
    }

    static String normalizeDurationUnitForDb(String unit) {
        if (unit != null && "hours".equalsIgnoreCase(unit.trim())) {
            return "hours";
        }
        if (unit != null && "permanent".equalsIgnoreCase(unit.trim())) {
            return "permanent";
        }
        return "days";
    }
}
