package com.lanxinai.data.paltform.ducklake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class DemoSchemaManager {

    private static final Logger log = LoggerFactory.getLogger(DemoSchemaManager.class);

    private final JdbcTemplate jdbcTemplate;
    private final DemoTable table;

    public DemoSchemaManager(JdbcTemplate jdbcTemplate, DemoTable table) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = table;
    }

    public void initialize() {
        long started = System.nanoTime();
        log.info("检查 DuckLake 测试表：{}", table.qualifiedName());
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS " + table.qualifiedSchema());
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS %s (
                  id VARCHAR NOT NULL,
                  batch_id VARCHAR NOT NULL,
                  name VARCHAR NOT NULL,
                  quantity INTEGER NOT NULL,
                  amount DECIMAL(18, 2) NOT NULL,
                  status VARCHAR NOT NULL,
                  remark VARCHAR,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """.formatted(table.qualifiedName()));
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        log.info("DuckLake 测试表已就绪：{}，耗时 {} ms", table.qualifiedName(), elapsedMs);
    }

    public String qualifiedTableName() {
        return table.qualifiedName();
    }
}
