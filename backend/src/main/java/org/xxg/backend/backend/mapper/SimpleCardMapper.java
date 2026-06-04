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

    public List<Card> listStackAnchorCandidates(String machineCode, Long excludeCardId) {
        if (machineCode == null || machineCode.isEmpty()) {
            return new ArrayList<>();
        }
        Long ex = excludeCardId != null ? excludeCardId : -1L;
        String sql = "SELECT * FROM simple_cards WHERE machine_code = ? AND card_type = 'time' AND status = 1 AND id <> ?";
        return jdbcTemplate.query(sql, new SimpleCardRowMapper(), machineCode, ex);
    }

    public void batchInsert(List<Card> cards) {
        String sql = "INSERT INTO simple_cards (card_key, card_type, duration, total_count, remaining_count, status, " +
                "verify_method, allow_reverify, create_time, creator_type, creator_id, creator_name, api_key_id, " +
                "stack_time_if_same_machine, allow_self_unbind) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Card card = cards.get(i);
                ps.setString(1, card.getCardKey());
                ps.setString(2, card.getCardType());
                ps.setInt(3, card.getDuration());
                ps.setInt(4, card.getTotalCount());
                ps.setInt(5, card.getRemainingCount());
                ps.setInt(6, card.getStatus());
                ps.setString(7, card.getVerifyMethod());
                ps.setInt(8, card.getAllowReverify());
                ps.setTimestamp(9, Timestamp.valueOf(card.getCreateTime()));
                ps.setString(10, card.getCreatorType());
                ps.setLong(11, card.getCreatorId());
                ps.setString(12, card.getCreatorName());
                if (card.getApiKeyId() != null) {
                    ps.setLong(13, card.getApiKeyId());
                } else {
                    ps.setNull(13, java.sql.Types.BIGINT);
                }
                ps.setInt(14, Boolean.TRUE.equals(card.getStackTimeIfSameMachine()) ? 1 : 0);
                ps.setInt(15, Boolean.TRUE.equals(card.getAllowSelfUnbind()) ? 1 : 0);
            }

            @Override
            public int getBatchSize() {
                return cards.size();
            }
        });
    }

    public void update(Card card) {
        jdbcTemplate.update(
                "UPDATE simple_cards SET card_key=?, card_type=?, duration=?, total_count=?, remaining_count=?, " +
                        "status=?, verify_method=?, allow_reverify=?, use_time=?, expire_time=?, device_id=?, " +
                        "ip_address=?, machine_code=?, stack_time_if_same_machine=?, allow_self_unbind=?, " +
                        "merged_into_card_id=?, api_key_id=? WHERE id=?",
                card.getCardKey(),
                card.getCardType(),
                card.getDuration(),
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
            long merged = rs.getLong("merged_into_card_id");
            if (!rs.wasNull()) {
                card.setMergedIntoCardId(merged);
            }
            return card;
        }
    }
}
