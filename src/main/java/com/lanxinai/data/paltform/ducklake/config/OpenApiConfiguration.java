package com.lanxinai.data.paltform.ducklake.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI duckLakeDemoOpenApi() {
        return new OpenAPI().info(new Info()
                .title("DuckLake Spring Boot Demo API")
                .version("0.1.0")
                .description("通过 JDBC、JdbcTemplate、MyBatis 和 JPA/Hibernate 操作同一张 DuckLake 测试表。"));
    }
}
