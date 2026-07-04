package org.xxg.backend.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.service.RemoteUpdateService;
import org.xxg.backend.backend.service.ServerLocationService;
import org.xxg.backend.backend.service.SystemMonitorService;

import java.util.Map;
import java.util.Optional;

/**
 * 系统监控控制器
 */
@RestController
@RequestMapping("/monitor")
public class SystemMonitorController {

    @Autowired
    private SystemMonitorService systemMonitorService;

    @Autowired
    private RemoteUpdateService remoteUpdateService;

    @Autowired
    private ServerLocationService serverLocationService;

    /**
     * 获取数据库状态信息
     */
    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> getDatabaseStatus() {
        try {
            Map<String, Object> databaseStatus = systemMonitorService.getDatabaseStatus();
            return ResponseEntity.ok(databaseStatus);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取系统资源状态
     */
    @GetMapping("/system")
    public ResponseEntity<Map<String, Object>> getSystemStatus() {
        try {
            Map<String, Object> systemStatus = systemMonitorService.getSystemStatus();
            return ResponseEntity.ok(systemStatus);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取API服务状态
     */
    @GetMapping("/api")
    public ResponseEntity<Map<String, Object>> getApiStatus() {
        try {
            Map<String, Object> apiStatus = systemMonitorService.getApiStatus();
            return ResponseEntity.ok(apiStatus);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取在线用户信息
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getOnlineUsers() {
        try {
            Map<String, Object> onlineUsers = systemMonitorService.getOnlineUsers();
            return ResponseEntity.ok(onlineUsers);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取服务器公网 IP 及物理位置（国内 / 国外双源查询）
     */
    @GetMapping("/server-location")
    public ResponseEntity<Map<String, Object>> getServerLocation() {
        try {
            return ResponseEntity.ok(serverLocationService.getServerLocation());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", e.getMessage() != null ? e.getMessage() : "查询失败"
            ));
        }
    }

    /**
     * 获取所有监控数据
     */
    @GetMapping("/all")
    public ResponseEntity<Map<String, Object>> getAllMonitorData() {
        try {
            Map<String, Object> allData = systemMonitorService.getAllMonitorData();
            return ResponseEntity.ok(allData);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 检查更新
     */
    @GetMapping("/check-update")
    public ResponseEntity<?> checkUpdate() {
        try {
            Optional<RemoteUpdateService.FetchedVersion> fetched = remoteUpdateService.fetchBestRemoteVersionJson();
            if (fetched.isEmpty()) {
                return ResponseEntity.internalServerError().body(Map.of("error", "检查更新失败：无法获取远程版本信息"));
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Update-Channel", fetched.get().jsonSource().name().toLowerCase());
            headers.set("X-Update-Version-Url", remoteUpdateService.versionJsonUrl(fetched.get().jsonSource()));
            return ResponseEntity.ok().headers(headers).body(fetched.get().json());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "检查更新失败: " + e.getMessage()));
        }
    }
}