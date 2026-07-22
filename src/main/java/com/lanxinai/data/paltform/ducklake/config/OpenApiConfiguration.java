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
                .title("DuckLake Demo 与 ETL Bridge 验收 API")
                .version("0.1.0")
                .description("DuckLake 数据访问示例，以及服务端持有 token 的 Fresh Bridge v1 最薄验收 BFF。"));
    }
}
