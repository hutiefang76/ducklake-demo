package com.lanxinai.data.paltform.ducklake.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.file.Path;

@Configuration
public class DuckLakeDataSourceConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DuckLakeDataSourceConfiguration.class);

    @Bean
    public DemoTable demoTable(DuckLakeProperties properties) {
        properties.validate();
        return new DemoTable(properties.getAttachName(), properties.getSchemaName());
    }

    @Bean(destroyMethod = "close")
    public DataSource dataSource(DuckLakeProperties properties) {
        properties.validate();
        String duckDbPath = resolveDuckDbPath(properties);
        String jdbcUrl = "jdbc:duckdb:" + duckDbPath;
        log.info("创建 DuckDB 连接壳：path={}，DuckLake catalog={}:{}/{}，S3={}",
                duckDbPath, properties.getPgHost(), properties.getPgPort(), properties.getPgDatabase(),
                properties.getS3Endpoint());
        var hikari = new HikariConfig();
        hikari.setPoolName("ducklake-demo-pool");
        hikari.setDataSource(new InitializingDuckLakeDataSource(properties, jdbcUrl));
        hikari.setMaximumPoolSize(properties.getMaximumPoolSize());
        hikari.setMinimumIdle(1);
        hikari.setAutoCommit(true);
        hikari.setConnectionTestQuery("SELECT 1");
        hikari.setConnectionTimeout(30_000);
        hikari.setValidationTimeout(5_000);
        // DuckDB 是嵌入式引擎；本示例固定一个物理连接，远端共享层仍是 PostgreSQL + S3。
        return new HikariDataSource(hikari);
    }

    @Bean
    public ApplicationRunner createDemoTableOnStartup(DemoSchemaManager schemaManager) {
        return args -> schemaManager.initialize();
    }

    private static String resolveDuckDbPath(DuckLakeProperties properties) {
        if (properties.getDuckdbPath() != null && !properties.getDuckdbPath().isBlank()) {
            return Path.of(properties.getDuckdbPath()).toAbsolutePath().toString().replace('\\', '/');
        }
        long pid = ProcessHandle.current().pid();
        return Path.of(System.getProperty("java.io.tmpdir"), "ducklake-demo-" + pid + ".duckdb")
                .toAbsolutePath().toString().replace('\\', '/');
    }
}
