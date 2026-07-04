package org.xxg.backend.backend.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.util.AdminPermissions;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AdminMapper {

    private final JdbcTemplate jdbcTemplate;

    public AdminMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initColumns();
    }

    private void initColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN totp_secret VARCHAR(255) DEFAULT NULL");
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN totp_enabled BOOLEAN DEFAULT FALSE");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN email VARCHAR(100) DEFAULT NULL");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN status TINYINT(1) NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用'");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN is_super TINYINT(1) NOT NULL DEFAULT 0 COMMENT '超级管理员'");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN permissions TEXT NULL COMMENT '逗号分隔权限码'");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE admins ADD COLUMN created_by BIGINT NULL");
        } catch (Exception ignored) {
        }
        ensureSuperAdminExists();
    }

    private void ensureSuperAdminExists() {
        try {
            Integer superCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM admins WHERE is_super = 1", Integer.class);
            if (superCount != null && superCount > 0) {
                return;
            }
            List<Long> ids = jdbcTemplate.queryForList("SELECT id FROM admins ORDER BY id ASC LIMIT 1", Long.class);
            if (!ids.isEmpty()) {
                jdbcTemplate.update(
                        "UPDATE admins SET is_super = 1, permissions = ? WHERE id = ?",
                        AdminPermissions.join(AdminPermissions.allSet()),
                        ids.get(0)
                );
            }
        } catch (Exception ignored) {
        }
    }

    public Admin findByUsername(String username) {
        String sql = "SELECT * FROM admins WHERE username = ?";
        List<Admin> admins = jdbcTemplate.query(sql, new AdminRowMapper(), username);
        return admins.isEmpty() ? null : admins.get(0);
    }

    public Admin findById(Long id) {
        String sql = "SELECT * FROM admins WHERE id = ?";
        List<Admin> admins = jdbcTemplate.query(sql, new AdminRowMapper(), id);
        return admins.isEmpty() ? null : admins.get(0);
    }

    public List<Admin> findAll() {
        return jdbcTemplate.query("SELECT * FROM admins ORDER BY id ASC", new AdminRowMapper());
    }

    public int countAll() {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM admins", Integer.class);
        return n != null ? n : 0;
    }

    public void updateLastLogin(Long adminId, String accessToken, String refreshToken) {
        String sql = "UPDATE admins SET last_login = ?, access_token = ?, refresh_token = ? WHERE id = ?";
        jdbcTemplate.update(sql, LocalDateTime.now(), accessToken, refreshToken, adminId);
    }

    public void clearTokens(Long adminId) {
        jdbcTemplate.update("UPDATE admins SET access_token = NULL, refresh_token = NULL WHERE id = ?", adminId);
    }

    public void updateAdmin(Admin admin) {
        jdbcTemplate.update(
                "UPDATE admins SET username = ?, password = ?, email = ?, totp_secret = ?, totp_enabled = ?, " +
                        "status = ?, is_super = ?, permissions = ?, created_by = ? WHERE id = ?",
                admin.getUsername(),
                admin.getPassword(),
                admin.getEmail(),
                admin.getTotpSecret(),
                admin.getTotpEnabled() != null && admin.getTotpEnabled(),
                admin.getStatus() != null ? admin.getStatus() : 1,
                admin.isSuperAdmin() ? 1 : 0,
                admin.getPermissions(),
                admin.getCreatedBy(),
                admin.getId()
        );
    }

    public void updateProfile(Admin admin) {
        jdbcTemplate.update(
                "UPDATE admins SET username = ?, password = ?, email = ? WHERE id = ?",
                admin.getUsername(), admin.getPassword(), admin.getEmail(), admin.getId()
        );
    }

    public void updateTotp(Long id, String secret, boolean enabled) {
        jdbcTemplate.update("UPDATE admins SET totp_secret = ?, totp_enabled = ? WHERE id = ?", secret, enabled, id);
    }

    public Long insertAdmin(Admin admin) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO admins (username, password, email, create_time, status, is_super, permissions, created_by) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, admin.getUsername());
            ps.setString(2, admin.getPassword());
            ps.setString(3, admin.getEmail());
            ps.setObject(4, LocalDateTime.now());
            ps.setInt(5, admin.getStatus() != null ? admin.getStatus() : 1);
            ps.setInt(6, admin.isSuperAdmin() ? 1 : 0);
            ps.setString(7, admin.getPermissions());
            if (admin.getCreatedBy() != null) {
                ps.setLong(8, admin.getCreatedBy());
            } else {
                ps.setNull(8, java.sql.Types.BIGINT);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    public void deleteById(Long id) {
        jdbcTemplate.update("DELETE FROM admins WHERE id = ?", id);
    }

    private static class AdminRowMapper implements RowMapper<Admin> {
        @Override
        public Admin mapRow(ResultSet rs, int rowNum) throws SQLException {
            Admin admin = new Admin();
            admin.setId(rs.getLong("id"));
            admin.setUsername(rs.getString("username"));
            admin.setPassword(rs.getString("password"));
            try {
                admin.setEmail(rs.getString("email"));
            } catch (SQLException ignored) {
            }
            if (rs.getTimestamp("create_time") != null) {
                admin.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            }
            if (rs.getTimestamp("last_login") != null) {
                admin.setLastLogin(rs.getTimestamp("last_login").toLocalDateTime());
            }
            admin.setAccessToken(rs.getString("access_token"));
            try {
                admin.setRefreshToken(rs.getString("refresh_token"));
            } catch (SQLException ignored) {
            }
            try {
                admin.setTotpSecret(rs.getString("totp_secret"));
                admin.setTotpEnabled(rs.getBoolean("totp_enabled"));
            } catch (SQLException ignored) {
            }
            try {
                admin.setStatus(rs.getInt("status"));
            } catch (SQLException ignored) {
                admin.setStatus(1);
            }
            try {
                admin.setIsSuper(rs.getInt("is_super") == 1);
            } catch (SQLException ignored) {
            }
            try {
                admin.setPermissions(rs.getString("permissions"));
            } catch (SQLException ignored) {
            }
            try {
                long createdBy = rs.getLong("created_by");
                if (!rs.wasNull()) {
                    admin.setCreatedBy(createdBy);
                }
            } catch (SQLException ignored) {
            }
            return admin;
        }
    }
}
