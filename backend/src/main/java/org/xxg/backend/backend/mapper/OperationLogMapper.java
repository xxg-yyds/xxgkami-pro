package org.xxg.backend.backend.mapper;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.xxg.backend.backend.entity.OperationLog;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Repository
public class OperationLogMapper {

    private final JdbcTemplate jdbcTemplate;

    public OperationLogMapper(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        ensureTableExists();
    }

    private void ensureTableExists() {
        try {
            jdbcTemplate.execute(
                    "CREATE TABLE IF NOT EXISTS operation_logs (" +
                            "id INT NOT NULL AUTO_INCREMENT," +
                            "admin_id INT NOT NULL," +
                            "admin_username VARCHAR(50) NOT NULL," +
                            "operation_type VARCHAR(30) NOT NULL," +
                            "operation_content TEXT NOT NULL," +
                            "ip_address VARCHAR(50) NULL," +
                            "create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                            "PRIMARY KEY (id)," +
                            "INDEX idx_admin_id (admin_id)," +
                            "INDEX idx_operation_type (operation_type)," +
                            "INDEX idx_create_time (create_time)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
        } catch (Exception ignored) {
        }
    }

    public void insert(OperationLog log) {
        jdbcTemplate.update(
                "INSERT INTO operation_logs (admin_id, admin_username, operation_type, operation_content, ip_address, create_time) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                log.getAdminId(),
                log.getAdminUsername(),
                log.getOperationType(),
                log.getOperationContent(),
                log.getIpAddress(),
                log.getCreateTime() != null ? log.getCreateTime() : LocalDateTime.now()
        );
    }

    public List<OperationLog> search(String keyword, String operationType, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM operation_logs WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (operationType != null && !operationType.isBlank()) {
            sql.append(" AND operation_type = ?");
            args.add(operationType.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (admin_username LIKE ? OR operation_content LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY create_time DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbcTemplate.query(sql.toString(), new OperationLogRowMapper(), args.toArray());
    }

    public int countSearch(String keyword, String operationType) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM operation_logs WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (operationType != null && !operationType.isBlank()) {
            sql.append(" AND operation_type = ?");
            args.add(operationType.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (admin_username LIKE ? OR operation_content LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            args.add(like);
            args.add(like);
        }
        Integer n = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return n != null ? n : 0;
    }

    private static class OperationLogRowMapper implements RowMapper<OperationLog> {
        @Override
        public OperationLog mapRow(ResultSet rs, int rowNum) throws SQLException {
            OperationLog log = new OperationLog();
            log.setId(rs.getLong("id"));
            log.setAdminId(rs.getLong("admin_id"));
            log.setAdminUsername(rs.getString("admin_username"));
            log.setOperationType(rs.getString("operation_type"));
            log.setOperationContent(rs.getString("operation_content"));
            log.setIpAddress(rs.getString("ip_address"));
            if (rs.getTimestamp("create_time") != null) {
                log.setCreateTime(rs.getTimestamp("create_time").toLocalDateTime());
            }
            return log;
        }
    }
}
