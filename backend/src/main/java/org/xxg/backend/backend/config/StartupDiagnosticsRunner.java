package org.xxg.backend.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.xxg.backend.backend.service.MysqlRuntimeProbeService;
import org.xxg.backend.backend.service.SetupMarkerService;

import java.util.Map;

/**
 * 启动时输出安装/数据库状态，并探测 MySQL 版本推荐脚本系列。
 */
@Component
@Order(0)
public class StartupDiagnosticsRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StartupDiagnosticsRunner.class);

    private final SetupMarkerService setupMarkerService;
    private final MysqlRuntimeProbeService mysqlRuntimeProbeService;

    public StartupDiagnosticsRunner(SetupMarkerService setupMarkerService,
                                    MysqlRuntimeProbeService mysqlRuntimeProbeService) {
        this.setupMarkerService = setupMarkerService;
        this.mysqlRuntimeProbeService = mysqlRuntimeProbeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean marker = setupMarkerService.isSetupComplete();
        boolean business = setupMarkerService.isBusinessDatabaseReady();
        log.info("安装标记文件: {} (exists={})", setupMarkerService.resolveMarkerPath(), marker);
        log.info("业务库就绪: {}（未完成时请访问 /admin 走安装向导）", business);

        Map<String, Object> mysql = mysqlRuntimeProbeService.probe();
        if (Boolean.TRUE.equals(mysql.get("reachable"))) {
            log.info("MySQL 版本: {}，推荐种子脚本: {} ({})",
                    mysql.get("version"),
                    mysql.get("recommendedSqlSeries"),
                    mysql.get("recommendedLabel"));
        } else {
            log.warn("MySQL 暂不可连接: {}", mysql.get("error"));
        }
    }
}
