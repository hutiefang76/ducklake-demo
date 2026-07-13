package com.lanxinai.data.paltform.ducklake.etl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.controller.ApiExceptionHandler;
import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerFacadeService;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerConfig;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.controller.SchedulerController;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "RUN_ETL_FACADE_LIVE", matches = "true")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = EtlFacadeLiveHttpTest.TestApplication.class,
        properties = {
                "spring.main.banner-mode=off",
                "scheduler.enabled=true",
                "scheduler.base-url=${DS_BASE_URL}",
                "scheduler.username=${DS_USERNAME}",
                "scheduler.password=${DS_PASSWORD}",
                "scheduler.catalog-path=${ETL_SCHEDULER_CATALOG_PATH}",
                "etl-platform.enabled=true",
                "etl-platform.artifact.endpoint=${ETL_ARTIFACT_S3_ENDPOINT}",
                "etl-platform.artifact.region=${ETL_ARTIFACT_S3_REGION}",
                "etl-platform.artifact.access-key=${ETL_ARTIFACT_S3_ACCESS_KEY}",
                "etl-platform.artifact.secret-key=${ETL_ARTIFACT_S3_SECRET_KEY}",
                "etl-platform.artifact.bucket=${ETL_ARTIFACT_S3_BUCKET}",
                "etl-platform.artifact.prefix=${ETL_ARTIFACT_S3_PREFIX}",
                "etl-platform.ledger.jdbc-url=${ETL_LEDGER_JDBC_URL}",
                "etl-platform.ledger.username=${ETL_LEDGER_USERNAME}",
                "etl-platform.ledger.password=${ETL_LEDGER_PASSWORD}",
                "etl-platform.ledger.schema=${ETL_LEDGER_SCHEMA}"
        })
class EtlFacadeLiveHttpTest {

    private static final Set<String> TERMINAL_STATES = Set.of(
            "SUCCESS", "FAILURE", "STOP", "KILL", "PAUSE");

    @LocalServerPort
    private int port;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void exercisesRegisteredFacadeLifecycleAndDedicatedDuckLakeReadback() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String markerId = "acc_" + suffix;
        String markerValue = "facade-value-" + suffix;

        assertError(
                post("/api/v1/etl/projects/notebooks-etl/workflows/ops.ducklake_acceptance/runs",
                        "{\"parameters\":{\"operation\":\"upsert\",\"marker_id\":\"INVALID\"}}"),
                400);
        assertError(
                post("/api/scheduler/projects/notebooks-etl/workflows/ops.ducklake_acceptance/runs",
                        "{\"runId\":\"run_tampered\",\"uri\":\"s3://dp-ducklake/tampered.json\","
                                + "\"sha256\":\"0000000000000000000000000000000000000000000000000000000000000000\"}"),
                403);
        assertError(get("/api/scheduler/projects/notebooks-etl/runs/999999999/status"), 403);

        long first = startAndAwait("ops.ducklake_acceptance", "upsert", markerId, markerValue, "SUCCESS");
        long second = startAndAwait("ops.ducklake_acceptance", "upsert", markerId, markerValue, "SUCCESS");
        assertThat(second).isNotEqualTo(first);
        long readback = startAndAwait("ops.ducklake_acceptance", "readback", markerId, markerValue, "SUCCESS");

        JsonNode tasks = assertOk(get("/api/scheduler/projects/notebooks-etl/runs/" + readback + "/tasks"));
        assertThat(tasks.path("tasks").size()).isGreaterThanOrEqualTo(1);
        long taskId = tasks.path("tasks").get(0).path("id").asLong();
        JsonNode log = assertOk(get("/api/scheduler/projects/notebooks-etl/runs/" + readback
                + "/log?taskInstanceId=" + taskId + "&skipLineNum=0&limit=1000"));
        assertThat(log.path("message").asText())
                .contains("etl_task_complete")
                .doesNotContain(markerValue);
        assertError(get("/api/scheduler/projects/notebooks-etl/runs/" + readback
                + "/log?taskInstanceId=" + (taskId + 9_999_999L)), 400);

        long failed = start("ops.ducklake_acceptance", "readback", "acc_missing_" + suffix, markerValue);
        JsonNode failedStatus = awaitTerminal(failed, Duration.ofMinutes(3));
        assertThat(failedStatus.path("state").asText()).isEqualTo("FAILURE");
        JsonNode failedLog = assertOk(get("/api/scheduler/projects/notebooks-etl/runs/" + failed
                + "/log?skipLineNum=0&limit=1000"));
        assertThat(failedLog.path("message").asText())
                .contains("marker was not found")
                .doesNotContain(markerValue);

        long stoppable = start("ops.api_acceptance", null, null, "60");
        awaitStarted(stoppable, Duration.ofMinutes(2));
        JsonNode stopped = assertOk(post(
                "/api/scheduler/projects/notebooks-etl/runs/" + stoppable + "/stop", "{}"));
        assertThat(stopped.path("accepted").asBoolean()).isTrue();
        JsonNode stoppedStatus = awaitTerminal(stoppable, Duration.ofMinutes(2));
        assertThat(stoppedStatus.path("state").asText()).isIn("FAILURE", "STOP", "KILL");

        startAndAwait("ops.ducklake_acceptance", "cleanup", markerId, "ok", "SUCCESS");
        startAndAwait("ops.ducklake_acceptance", "cleanup", markerId, "ok", "SUCCESS");

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("schemaVersion", 1);
        evidence.put("generatedAt", Instant.now().toString());
        evidence.put("verdict", "PASS");
        evidence.put("markerId", markerId);
        evidence.put("workflowInstances", Map.of(
                "firstUpsert", first,
                "secondUpsert", second,
                "readback", readback,
                "failedReadback", failed,
                "stopped", stoppable));
        evidence.put("checks", List.of(
                "invalid-parameters-400",
                "tampered-manifest-403",
                "unowned-instance-403",
                "repeat-upsert-success",
                "ducklake-readback-success",
                "task-instance-mismatch-400",
                "marker-value-not-in-log",
                "failed-readback-with-retry-policy",
                "stop-accepted-and-terminal",
                "repeat-cleanup-success"));
        Path evidencePath = Path.of("target", "task007-live-facade-evidence.json");
        Files.createDirectories(evidencePath.getParent());
        Files.writeString(
                evidencePath,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(evidence),
                StandardCharsets.UTF_8);
    }

    private long startAndAwait(
            String workflowId,
            String operation,
            String markerId,
            String markerValue,
            String expectedState) throws Exception {
        long instanceId = start(workflowId, operation, markerId, markerValue);
        JsonNode status = awaitTerminal(instanceId, Duration.ofMinutes(3));
        assertThat(status.path("state").asText()).isEqualTo(expectedState);
        return instanceId;
    }

    private long start(String workflowId, String operation, String markerId, String markerValue)
            throws Exception {
        String parameters;
        if ("ops.api_acceptance".equals(workflowId)) {
            parameters = "{\"sleep_seconds\":" + Integer.parseInt(markerValue) + "}";
        } else {
            parameters = "{\"operation\":" + mapper.writeValueAsString(operation)
                    + ",\"marker_id\":" + mapper.writeValueAsString(markerId)
                    + ",\"marker_value\":" + mapper.writeValueAsString(markerValue) + "}";
        }
        JsonNode response = assertOk(post(
                "/api/v1/etl/projects/notebooks-etl/workflows/" + workflowId + "/runs",
                "{\"parameters\":" + parameters + ",\"reason\":\"TASK-007 live acceptance\"}"));
        return response.path("scheduler").path("workflowInstanceId").asLong();
    }

    private JsonNode awaitStarted(long instanceId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            last = assertOk(get("/api/scheduler/projects/notebooks-etl/runs/" + instanceId + "/status"));
            String state = last.path("state").asText();
            if (!state.equals("SUBMITTED_SUCCESS") && !state.equals("READY_STOP")) return last;
            Thread.sleep(1000);
        }
        throw new AssertionError("Workflow did not start before timeout: " + last);
    }

    private JsonNode awaitTerminal(long instanceId, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        JsonNode last = null;
        while (System.nanoTime() < deadline) {
            last = assertOk(get("/api/scheduler/projects/notebooks-etl/runs/" + instanceId + "/status"));
            if (TERMINAL_STATES.contains(last.path("state").asText())) return last;
            Thread.sleep(1000);
        }
        throw new AssertionError("Workflow did not become terminal before timeout: " + last);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(uri(path))
                        .header("Content-Type", "application/json")
                        .header("X-Requested-By", "task007-live-test")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + port + path);
    }

    private JsonNode assertOk(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isBetween(200, 299);
        return mapper.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, int status) throws Exception {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(mapper.readTree(response.body()).path("status").asInt()).isEqualTo(status);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class
    })
    @EnableConfigurationProperties({SchedulerProperties.class, EtlPlatformProperties.class})
    @Import({
            SchedulerConfig.class,
            SchedulerCatalogService.class,
            DolphinSchedulerClient.class,
            SchedulerFacadeService.class,
            SchedulerController.class,
            EtlLedgerRepository.class,
            EtlSchedulerRunRegistry.class,
            EtlArtifactService.class,
            EtlParameterValidator.class,
            EtlOrchestrationService.class,
            EtlController.class,
            ApiExceptionHandler.class
    })
    static class TestApplication {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
