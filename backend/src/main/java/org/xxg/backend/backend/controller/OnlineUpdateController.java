package org.xxg.backend.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.xxg.backend.backend.service.OnlineUpdateService;

import java.util.Map;

@RestController
@RequestMapping("/monitor/update")
@PreAuthorize("hasRole('ADMIN')")
public class OnlineUpdateController {

    private final OnlineUpdateService onlineUpdateService;

    public OnlineUpdateController(OnlineUpdateService onlineUpdateService) {
        this.onlineUpdateService = onlineUpdateService;
    }

    @GetMapping("/paths")
    public ResponseEntity<Map<String, Object>> detectPaths() {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data", onlineUpdateService.detectPaths()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", e.getMessage() != null ? e.getMessage() : "路径检测失败"
            ));
        }
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("success", true, "data", onlineUpdateService.getStatus()));
    }

    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        try {
            String jar = str(body.get("backendJarPath"));
            String dist = str(body.get("frontendDistPath"));
            if (jar.isEmpty() || dist.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "请填写后端 JAR 与前端 dist 路径"));
            }
            onlineUpdateService.startUpdate(jar, dist);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "在线更新已开始",
                    "data", onlineUpdateService.getStatus()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage() != null ? e.getMessage() : "启动更新失败"
            ));
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
