package org.xxg.backend.backend.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.xxg.backend.backend.service.SetupService;
import org.xxg.backend.backend.service.SqlTranslateService;

import java.util.Map;

/**
 * 首次安装向导 API（无需登录）。
 */
@RestController
@RequestMapping("/setup")
public class SetupController {

    @Autowired
    private SetupService setupService;

    @Autowired
    private SqlTranslateService sqlTranslateService;

    @PostMapping("/sql/translate")
    public ResponseEntity<Map<String, Object>> startSqlTranslate(@RequestBody(required = false) Map<String, Object> body) {
        String series = body != null ? str(body.get("sqlSeries"), "56") : "56";
        if ("80".equals(series) || "8".equals(series)) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", Map.of("ready", true, "skipped", true, "message", "8.0 无需转译")
            ));
        }
        sqlTranslateService.startTranslateAsync();
        return ResponseEntity.ok(Map.of("success", true, "data", sqlTranslateService.getStatus()));
    }

    @GetMapping("/sql/translate/status")
    public ResponseEntity<Map<String, Object>> sqlTranslateStatus() {
        return ResponseEntity.ok(Map.of("success", true, "data", sqlTranslateService.getStatus()));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("success", true, "data", setupService.getStatus()));
    }

    @GetMapping("/environment")
    public ResponseEntity<Map<String, Object>> environment() {
        return ResponseEntity.ok(Map.of("success", true, "data", setupService.getEnvironment()));
    }

    @PostMapping("/mysql/test")
    public ResponseEntity<Map<String, Object>> testMysql(@RequestBody Map<String, Object> body) {
        String host = str(body.get("host"), "localhost");
        int port = intVal(body.get("port"), 3306);
        String user = str(body.get("username"), "root");
        String pass = str(body.get("password"), "");
        Map<String, Object> data = setupService.testMysqlConnection(host, port, user, pass);
        boolean ok = Boolean.TRUE.equals(data.get("ok"));
        return ResponseEntity.ok(Map.of("success", ok, "data", data, "message", ok ? "连接成功" : data.get("message")));
    }

    @PostMapping("/mysql/check-db")
    public ResponseEntity<Map<String, Object>> checkDb(@RequestBody Map<String, Object> body) {
        String host = str(body.get("host"), "localhost");
        int port = intVal(body.get("port"), 3306);
        String user = str(body.get("username"), "root");
        String pass = str(body.get("password"), "");
        Map<String, Object> data = setupService.checkKamiDatabase(host, port, user, pass);
        boolean ok = !Boolean.FALSE.equals(data.get("ok"));
        return ResponseEntity.ok(Map.of(
                "success", ok,
                "data", data,
                "message", ok ? "检测完成" : data.get("message")
        ));
    }

    @PostMapping("/mysql/analyze-merge-configured")
    public ResponseEntity<Map<String, Object>> analyzeMergeConfigured() {
        try {
            Map<String, Object> data = setupService.analyzeMergeUsingConfigured();
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/mysql/install-configured")
    public ResponseEntity<Map<String, Object>> installConfigured(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> data = setupService.installUsingConfigured(body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/mysql/analyze-merge")
    public ResponseEntity<Map<String, Object>> analyzeMerge(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> data = setupService.analyzeMergeDatabase(body);
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/mysql/install")
    public ResponseEntity<Map<String, Object>> install(@RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> data = setupService.installDatabase(body);
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/complete")
    public ResponseEntity<Map<String, Object>> complete(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> data = setupService.completeSetup(body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/version-upgrade/complete")
    public ResponseEntity<Map<String, Object>> completeVersionUpgrade(@RequestBody(required = false) Map<String, Object> body) {
        try {
            Map<String, Object> data = setupService.completeVersionUpgrade(body != null ? body : Map.of());
            return ResponseEntity.ok(Map.of("success", true, "data", data));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    private static String str(Object o, String def) {
        return o == null || o.toString().isBlank() ? def : o.toString().trim();
    }

    private static int intVal(Object o, int def) {
        if (o == null) {
            return def;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(o.toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
