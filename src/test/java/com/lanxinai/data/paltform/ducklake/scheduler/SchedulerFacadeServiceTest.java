package com.lanxinai.data.paltform.ducklake.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalogService;
import com.lanxinai.data.paltform.ducklake.scheduler.client.DolphinSchedulerClient;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunRequest;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SchedulerFacadeServiceTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper mapper = new ObjectMapper();
    private MockWebServer server;
    private SchedulerFacadeService facade;
    private Path catalogPath;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        catalogPath = tempDir.resolve("catalog.json");
        Files.writeString(catalogPath, """
                {
                  "schemaVersion": 1,
                  "generatedFrom": "data-platform/data-platform-notebooks",
                  "gitRef": "refs/heads/codex/etl-contract-v2",
                  "execution": {
                    "tenantCode": "etl-tenant",
                    "workerGroup": "etl-workers",
                    "failureStrategy": "CONTINUE",
                    "warningType": "NONE",
                    "warningGroupId": 0,
                    "runMode": "RUN_MODE_SERIAL",
                    "workflowInstancePriority": "HIGH",
                    "environmentCode": -1
                  },
                  "projects": [{
                    "id": "project-a",
                    "name": "Project A",
                    "code": 42,
                    "workflows": [
                      {
                        "id": "workflow-a",
                        "name": "Workflow A",
                        "code": 77,
                        "runManifestParameter": "run_manifest_uri",
                        "runManifestSha256Parameter": "run_manifest_sha256",
                        "parameterSchema": {},
                        "nodes": [{
                          "id": "node-alpha",
                          "name": "Alpha",
                          "taskId": "app_etl.alpha",
                          "code": 1001,
                          "taskType": "SHELL"
                        }]
                      },
                      {
                        "id": "workflow-b",
                        "name": "Workflow B",
                        "code": 88,
                        "runManifestParameter": "run_manifest_uri",
                        "runManifestSha256Parameter": "run_manifest_sha256",
                        "parameterSchema": {},
                        "nodes": [{
                          "id": "node-beta",
                          "name": "Beta",
                          "taskId": "ods_etl.beta",
                          "code": 2002,
                          "taskType": "SHELL"
                        }]
                      }
                    ],
                    "taskGroups": [{
                      "id": "ducklake-writers",
                      "name": "DuckLake Writers",
                      "groupId": 5
                    }]
                  }]
                }
                """);

        SchedulerProperties properties = new SchedulerProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(server.url("/dolphinscheduler").toString());
        properties.setToken("test-token-not-a-secret");
        properties.setCatalogPath(catalogPath);
        properties.setRequestTimeout(Duration.ofSeconds(5));

        SchedulerCatalogService catalog = new SchedulerCatalogService(properties, mapper);
        DolphinSchedulerClient client = new DolphinSchedulerClient(
                properties, mapper, HttpClient.newHttpClient());
        facade = new SchedulerFacadeService(properties, catalog, client);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void resolvesLogicalWorkflowAndNodeWithoutHardCodedCodes() throws Exception {
        enqueue("{\"code\":0,\"success\":true,\"data\":[9001]}");

        var response = facade.startRun(
                "project-a",
                "workflow-b",
                new RunRequest("node-beta", "s3://etl-runs/manifests/run-1.json", "abc123"));

        assertThat(response.workflowInstanceId()).isEqualTo(9001L);
        assertThat(response.taskId()).isEqualTo("ods_etl.beta");
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath()).isEqualTo(
                "/dolphinscheduler/projects/42/executors/start-workflow-instance");
        Map<String, String> form = decodeForm(request.getBody().readUtf8());
        assertThat(form)
                .containsEntry("workflowDefinitionCode", "88")
                .containsEntry("startNodeList", "2002")
                .containsEntry("tenantCode", "etl-tenant")
                .containsEntry("workerGroup", "etl-workers")
                .containsEntry("failureStrategy", "CONTINUE")
                .containsEntry("workflowInstancePriority", "HIGH");
        JsonNode startParams = mapper.readTree(form.get("startParams"));
        assertThat(startParams).hasSize(2);
        assertThat(startParams.get(0).path("prop").asText()).isEqualTo("run_manifest_uri");
        assertThat(startParams.get(0).path("value").asText())
                .isEqualTo("s3://etl-runs/manifests/run-1.json");
        assertThat(startParams.get(1).path("prop").asText()).isEqualTo("run_manifest_sha256");
        assertThat(startParams.get(1).path("value").asText()).isEqualTo("abc123");
    }

    @Test
    void startsWholeWorkflowWithoutStartNodeList() throws Exception {
        enqueue("{\"code\":0,\"success\":true,\"data\":[9010]}");

        var response = facade.startWorkflow(
                "project-a", "workflow-b",
                new RunManifestRef("run-10", "s3://etl-runs/manifests/run-10.json", "sha10"));

        assertThat(response.workflowInstanceId()).isEqualTo(9010L);
        assertThat(response.nodeId()).isNull();
        Map<String, String> form = decodeForm(server.takeRequest().getBody().readUtf8());
        assertThat(form).doesNotContainKey("startNodeList");
        assertThat(mapper.readTree(form.get("startParams"))).hasSize(2);
    }

    @Test
    void rejectsUnknownLogicalObjectBeforeCallingDolphinScheduler() {
        assertThatThrownBy(() -> facade.startRun(
                "project-a",
                "missing-workflow",
                new RunRequest("node-beta", "s3://etl-runs/manifests/run-2.json", "abc456")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown managed workflow");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void reloadsCatalogForEachRequestWithoutApplicationRestart() throws Exception {
        String catalog = Files.readString(catalogPath);
        Files.writeString(catalogPath, catalog
                .replace("\"code\": 88", "\"code\": 99")
                .replace("\"code\": 2002", "\"code\": 3003"));
        enqueue("{\"code\":0,\"success\":true,\"data\":[9002]}");

        facade.startRun(
                "project-a",
                "workflow-b",
                new RunRequest("node-beta", "s3://etl-runs/manifests/run-3.json", "abc789"));

        Map<String, String> form = decodeForm(server.takeRequest().getBody().readUtf8());
        assertThat(form)
                .containsEntry("workflowDefinitionCode", "99")
                .containsEntry("startNodeList", "3003");
    }

    @Test
    void readsStatusTasksLogAndStopsThroughDocumentedApis() throws Exception {
        enqueue("{\"code\":0,\"data\":{\"id\":9001,\"name\":\"run-1\",\"state\":\"RUNNING_EXECUTION\"}}");
        enqueueTasks();
        enqueueTasks();
        enqueue("{\"code\":0,\"data\":{\"message\":\"hello from worker\"}}");
        enqueue("{\"code\":0,\"success\":true,\"data\":null}");

        assertThat(facade.status("project-a", 9001).state()).isEqualTo("RUNNING_EXECUTION");
        assertThat(facade.tasks("project-a", 9001).tasks()).hasSize(1);
        assertThat(facade.log("project-a", 9001, null, 0, 20000).message())
                .isEqualTo("hello from worker");
        assertThat(facade.stop("project-a", 9001).accepted()).isTrue();

        assertThat(server.takeRequest().getPath())
                .isEqualTo("/dolphinscheduler/projects/42/workflow-instances/9001");
        assertThat(server.takeRequest().getPath()).contains("/workflow-instances/9001/tasks");
        assertThat(server.takeRequest().getPath()).contains("/workflow-instances/9001/tasks");
        assertThat(server.takeRequest().getPath()).contains("/dolphinscheduler/log/detail");
        RecordedRequest stop = server.takeRequest();
        assertThat(stop.getPath()).isEqualTo("/dolphinscheduler/projects/42/executors/execute");
        assertThat(decodeForm(stop.getBody().readUtf8()))
                .containsEntry("workflowInstanceId", "9001")
                .containsEntry("executeType", "STOP");
    }

    @Test
    void exposesTaskGroupQueueAsSnapshotNotExactPosition() throws Exception {
        enqueue("""
                {"code":0,"data":{"total":2,"totalList":[
                  {"id":31,"taskId":501,"taskName":"load-material","workflowInstanceId":9001,
                   "workflowInstanceName":"run-a","priority":2,"status":"WAIT_QUEUE","createTime":"t1"},
                  {"id":32,"taskId":502,"taskName":"load-order","workflowInstanceId":9002,
                   "workflowInstanceName":"run-b","priority":1,"status":"WAIT_QUEUE","createTime":"t2"}
                ]}}
                """);

        var queue = facade.queue("project-a", "ducklake-writers", 1, 1, 20);

        assertThat(queue.exactPosition()).isFalse();
        assertThat(queue.items()).extracting("snapshotIndex", "taskName", "workflowInstanceName")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(1, "load-material", "run-a"),
                        org.assertj.core.groups.Tuple.tuple(2, "load-order", "run-b"));
        assertThat(server.takeRequest().getPath())
                .isEqualTo("/dolphinscheduler/task-group/query-list-by-group-id?groupId=5&pageNo=1&pageSize=20&status=1");
    }

    private void enqueueTasks() {
        enqueue("{\"code\":0,\"data\":{\"taskList\":[{\"id\":501,\"name\":\"alpha\"}]}}");
    }

    private void enqueue(String body) {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    private static Map<String, String> decodeForm(String body) {
        return Arrays.stream(body.split("&"))
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> decode(pair[0]),
                        pair -> pair.length == 2 ? decode(pair[1]) : "",
                        (left, right) -> right,
                        LinkedHashMap::new));
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
