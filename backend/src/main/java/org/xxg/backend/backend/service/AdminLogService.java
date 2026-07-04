package org.xxg.backend.backend.service;

import org.springframework.stereotype.Service;
import org.xxg.backend.backend.entity.Admin;
import org.xxg.backend.backend.entity.OperationLog;
import org.xxg.backend.backend.mapper.OperationLogMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AdminLogService {

    private final OperationLogMapper operationLogMapper;

    public AdminLogService(OperationLogMapper operationLogMapper) {
        this.operationLogMapper = operationLogMapper;
    }

    public void log(Admin admin, String operationType, String content, String ipAddress) {
        if (admin == null) {
            return;
        }
        OperationLog log = new OperationLog();
        log.setAdminId(admin.getId());
        log.setAdminUsername(admin.getUsername());
        log.setOperationType(operationType);
        log.setOperationContent(content != null ? content : "");
        log.setIpAddress(ipAddress);
        log.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(log);
    }

    public Map<String, Object> list(String keyword, String operationType, int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(Math.max(pageSize, 1), 100);
        int offset = (safePage - 1) * safeSize;
        List<OperationLog> items = operationLogMapper.search(keyword, operationType, offset, safeSize);
        int total = operationLogMapper.countSearch(keyword, operationType);
        return Map.of(
                "items", items,
                "total", total,
                "page", safePage,
                "pageSize", safeSize
        );
    }
}
