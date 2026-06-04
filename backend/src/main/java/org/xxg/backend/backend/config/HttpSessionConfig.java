package org.xxg.backend.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

/**
 * 强制 Session 表名为 spring_session（与种子 SQL 一致，避免默认 SPRING_SESSION 大写表名）。
 */
@Configuration
@EnableJdbcHttpSession(maxInactiveIntervalInSeconds = 1800, tableName = "spring_session")
public class HttpSessionConfig {
}
