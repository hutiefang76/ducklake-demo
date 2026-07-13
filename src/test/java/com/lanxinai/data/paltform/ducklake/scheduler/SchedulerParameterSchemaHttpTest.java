package com.lanxinai.data.paltform.ducklake.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerConfig;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.controller.SchedulerController;
import com.lanxinai.data.paltform.ducklake.controller.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        classes = SchedulerParameterSchemaHttpTest.TestApplication.class,
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "scheduler.enabled=true",
                "scheduler.base-url=http://127.0.0.1:1/dolphinscheduler",
                "scheduler.token=test-token-not-a-secret"
        })
class SchedulerParameterSchemaHttpTest {

    @LocalServerPort
    int port;

    @Autowired
    ApplicationContext applicationContext;

    @DynamicPropertySource
    static void schedulerCatalog(DynamicPropertyRegistry registry) {
        registry.add("scheduler.catalog-path", () -> catalogPath().toString());
    }

    @Test
    void servesVersionedChineseParameterSchemaOverRealHttp() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port
                + "/api/scheduler/projects/notebooks-etl/workflows/"
                + "material.master_refresh/parameter-schema");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        ObjectMapper mapper = new ObjectMapper();
        JsonNode body = mapper.readTree(response.body());
        JsonNode catalog = mapper.readTree(catalogPath().toFile());
        JsonNode expectedWorkflow = null;
        for (JsonNode workflow : catalog.path("projects").get(0).path("workflows")) {
            if ("material.master_refresh".equals(workflow.path("id").asText())) {
                expectedWorkflow = workflow;
                break;
            }
        }

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(expectedWorkflow).isNotNull();
        assertThat(body.path("projectId").asText()).isEqualTo("notebooks-etl");
        assertThat(body.path("workflowId").asText()).isEqualTo("material.master_refresh");
        assertThat(body.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(body.path("sha256").asText())
                .isEqualTo(expectedWorkflow.path("parameterSchemaSha256").asText());
        assertThat(body.path("parameterSchema"))
                .isEqualTo(expectedWorkflow.path("parameterSchema"));
        assertThat(body.path("parameterSchema").toString())
                .containsPattern("[\\u4E00-\\u9FFF]");
    }

    @Test
    void rejectsUnownedWorkflowInstanceOverRealHttp() throws Exception {
        URI uri = URI.create("http://127.0.0.1:" + port
                + "/api/scheduler/projects/notebooks-etl/runs/999/status");
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        JsonNode body = new ObjectMapper().readTree(response.body());
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(body.path("error").asText())
                .isEqualTo("Scheduler resource is not authorized");
    }

    private static Path catalogPath() {
        return Path.of(System.getProperty(
                "etl.catalog.path",
                Path.of("src", "test", "resources", "scheduler-catalog-http-test.json").toString()))
                .toAbsolutePath().normalize();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration"
    })
    @EnableConfigurationProperties(SchedulerProperties.class)
    @Import({
            SchedulerConfig.class,
            SchedulerCatalogService.class,
            DolphinSchedulerClient.class,
            SchedulerFacadeService.class,
            SchedulerController.class,
            ApiExceptionHandler.class
    })
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        SchedulerRunRegistry schedulerRunRegistry() {
            return new SchedulerRunRegistry() {
                @Override
                public String requireAuthorizedManifest(
                        String projectId,
                        String workflowId,
                        com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef manifest) {
                    throw new SchedulerAuthorizationException("Not available in metadata-only test");
                }

                @Override
                public void attachWorkflowInstance(String runId, long workflowInstanceId) {
                    throw new SchedulerAuthorizationException("Not available in metadata-only test");
                }

                @Override
                public void requireOwnedInstance(String projectId, long workflowInstanceId) {
                    throw new SchedulerAuthorizationException("Not available in metadata-only test");
                }

                @Override
                public RunStateMetadata recordStatus(
                        String projectId, long workflowInstanceId, String schedulerState) {
                    throw new SchedulerAuthorizationException("Not available in metadata-only test");
                }
            };
        }
    }
}
