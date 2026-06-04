package org.xxg.backend.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;

/**
 * 在 Spring Session 定时任务之前创建 spring_session 表（业务库未导入时也可启动）。
 */
@Configuration
public class SpringSessionSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SpringSessionSchemaInitializer.class);

    private final DataSource dataSource;

    public SpringSessionSchemaInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void initSessionTables() {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                    false,
                    false,
                    "UTF-8",
                    new ClassPathResource("schema/spring-session-mysql.sql")
            );
            populator.setContinueOnError(true);
            populator.setSeparator(";");
            populator.execute(dataSource);
            log.info("Spring Session 表 spring_session / spring_session_ATTRIBUTES 已就绪");
        } catch (Exception e) {
            log.warn("Spring Session 表初始化失败: {}", e.getMessage());
        }
    }
}
