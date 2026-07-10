package com.lanxinai.data.paltform.ducklake.config;

import com.lanxinai.data.paltform.ducklake.support.DemoSql;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DemoSqlConfiguration {

    @Bean
    public DemoSql demoSql(DemoTable table) {
        return new DemoSql(table);
    }
}
