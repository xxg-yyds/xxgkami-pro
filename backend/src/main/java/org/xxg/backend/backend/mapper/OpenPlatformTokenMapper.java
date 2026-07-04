package org.xxg.backend.backend.mapper;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.xxg.backend.backend.entity.OpenPlatformToken;
import org.xxg.backend.backend.service.SetupMarkerService;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OpenPlatformTokenMapper {

    private static final Logger logger = LoggerFactory.getLogger(OpenPlatformTokenMapper.class);

    private final JdbcTemplate jdbcTemplate;
    private final SetupMarkerService setupMarkerService;

    public OpenPlatformTokenMapper(JdbcTemplate jdbcTemplate, SetupMarkerService setupMarkerService) {
        this.jdbcTemplate = jdbcTemplate;
        this.setupMarkerService = setupMarkerService;
    }

    @PostConstruct
    public void initSchema() {
        if (!setupMarkerService.isBusinessDatabaseReady()) {
            return;
        }
        try {
            jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS open_platform_tokens (" +
                    "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                    "name VARCHAR(100) NOT NULL COMMENT 'Token 名称', " +
                    "description VARCHAR(255) NULL, " +
                    "token_hash CHAR(64) NOT NULL COMMENT 'SHA-256 哈希', " +
                    "token_prefix VARCHAR(32) NOT NULL COMMENT '展示前缀', " +
                    "status TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=启用 0=吊销', " +
                    "created_by_admin_id BIGINT NULL, " +
                    "created_by_admin_name VARCHAR(64) NULL, " +
                    "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, " +
                    "last_use_time DATETIME NULL, " +
                    "expires_at DATETIME NULL, " +
                    "UNIQUE KEY uk_token_hash (token_hash)" +
                    ")");
            logger.info("open_platform_tokens schema ready");
        } catch (Exception e) {
            logger.error("Failed to init open_platform_tokens", e);
        }
    }

    public Long insert(OpenPlatformToken token) {
        String sql = "INSERT INTO open_platform_tokens (name, description, token_hash, token_prefix, status, " +
                "created_by_admin_id, created_by_admin_name, create_time, expires_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, token.getName());
            ps.setString(2, token.getDescription());
            ps.setString(3, token.getTokenHash());
            ps.setString(4, token.getTokenPrefix());
            ps.setInt(5, token.getStatus() != null ? token.getStatus() : 1);
            if (token.getCreatedByAdminId() != null) {
                ps.setLong(6, token.getCreatedByAdminId());
            } else {
                ps.setNull(6, java.sql.Types.BIGINT);
            }
            ps.setString(7, token.getCreatedByAdminName());
            ps.setTimestamp(8, Timestamp.valueOf(now));
            if (token.getExpiresAt() != null) {
                ps.setTimestamp(9, Timestamp.valueOf(token.getExpiresAt()));
            } else {
                ps.setNull(9, java.sql.Types.TIMESTAMP);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public OpenPlatformToken findByHash(String tokenHash) {
        String sql = "SELECT * FROM open_platform_tokens WHERE token_hash = ? LIMIT 1";
        List<OpenPlatformToken> list = jdbcTemplate.query(sql, new TokenRowMapper(), tokenHash);
        return list.isEmpty() ? null : list.get(0);
    }

    public List<OpenPlatformToken> findByAdminId(Long adminId) {
        return jdbcTemplate.query(
                "SELECT * FROM open_platform_tokens WHERE created_by_admin_id = ? ORDER BY create_time DESC",
                new TokenRowMapper(),
                adminId
        );
    }

    public int countByAdminId(Long adminId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM open_platform_tokens WHERE created_by_admin_id = ?",
                Integer.class,
                adminId
        );
        return count != null ? count : 0;
    }

    public void updateLastUseTime(Long id) {
        jdbcTemplate.update(
                "UPDATE open_platform_tokens SET last_use_time = ? WHERE id = ?",
                Timestamp.valueOf(LocalDateTime.now()), id
        );
    }

    public int deleteByIdAndAdminId(Long id, Long adminId) {
        return jdbcTemplate.update(
                "DELETE FROM open_platform_tokens WHERE id = ? AND created_by_admin_id = ?",
                id, adminId
        );
    }

    private static class TokenRowMapper implements RowMapper<OpenPlatformToken> {
        @Override
        public OpenPlatformToken mapRow(ResultSet rs, int rowNum) throws SQLException {
            OpenPlatformToken t = new OpenPlatformToken();
            t.setId(rs.getLong("id"));
            t.setName(rs.getString("name"));
            t.setDescription(rs.getString("description"));
            t.setTokenHash(rs.getString("token_hash"));
            t.setTokenPrefix(rs.getString("token_prefix"));
            t.setStatus(rs.getInt("status"));
            long adminId = rs.getLong("created_by_admin_id");
            if (!rs.wasNull()) {
                t.setCreatedByAdminId(adminId);
            }
            t.setCreatedByAdminName(rs.getString("created_by_admin_name"));
            Timestamp ct = rs.getTimestamp("create_time");
            if (ct != null) {
                t.setCreateTime(ct.toLocalDateTime());
            }
            Timestamp lut = rs.getTimestamp("last_use_time");
            if (lut != null) {
                t.setLastUseTime(lut.toLocalDateTime());
            }
            Timestamp exp = rs.getTimestamp("expires_at");
            if (exp != null) {
                t.setExpiresAt(exp.toLocalDateTime());
            }
            return t;
        }
    }
}
