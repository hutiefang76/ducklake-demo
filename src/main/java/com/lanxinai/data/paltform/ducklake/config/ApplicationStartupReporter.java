package com.lanxinai.data.paltform.ducklake.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationStartupReporter {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupReporter.class);

    private final DuckLakeProperties properties;
    private final int serverPort;
    private final String localEnvSource;

    public ApplicationStartupReporter(DuckLakeProperties properties,
                                      @Value("${server.port:8080}") int serverPort,
                                      @Value("${ducklake.local-env-source:未使用本地 .env}") String localEnvSource) {
        this.properties = properties;
        this.serverPort = serverPort;
        this.localEnvSource = localEnvSource;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reportReady() {
        // 只打印定位问题需要的非敏感信息，绝不打印 PostgreSQL/S3 密码。
        log.info("DuckLake Demo 已就绪：Swagger UI=http://127.0.0.1:{}/swagger-ui.html", serverPort);
        log.info("OpenAPI JSON=http://127.0.0.1:{}/v3/api-docs", serverPort);
        log.info("配置来源：{}（环境变量和启动参数可覆盖）", localEnvSource);
        log.info("DuckLake catalog={}:{}/{}，user={}，attach={}",
                properties.getPgHost(), properties.getPgPort(), properties.getPgDatabase(),
                properties.getPgUser(), properties.getAttachName());
        log.info("DuckLake data={}，S3 endpoint={}", properties.getDataPath(), properties.getS3Endpoint());
    }
}
