package com.lanxinai.data.paltform.ducklake.bridge;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeConsoleAssetsTest {

    @Test
    void consoleUsesOnlyFreshBridgeBusinessRoutes() throws IOException {
        String html = resource("static/etl-console.html");
        String script = resource("static/bridge-console/app.js");
        String legacyScript = resource("static/etl-console.js");

        assertThat(html).contains("script_name", "script_id", "源码分支与扫描", "全局队列", "执行历史", "停止执行",
                "scan-refresh", "run-refresh", "name=\"support_level\"", "name=\"runnable\"",
                "id=\"scan-ref\"", "切换并扫描",
                "类型 1 · 原生 Python", "类型 2 · 自动参数", "类型 3 · 完整 ETL 契约",
                "Demo → Bridge → DolphinScheduler 调用证据");
        assertThat(script).contains(
                "api(\"/scans/options\")",
                "api(\"/scans/latest\")",
                "api(\"/scans\"",
                "JSON.stringify({ repository_ref: repositoryRef })",
                "api(`/scripts?",
                "api(`/runs/current?",
                "api(\"/queue\")",
                "/logs?limit=500",
                "/stop`",
                "byId(\"scan-refresh\")",
                "byId(\"run-refresh\")");
        assertThat(script).contains(
                "function automaticParameters(entry)",
                "类型 1 固定无参数",
                "query.set(\"support_level\", supportLevel)",
                "query.set(\"runnable\", runnable)",
                "spec.allowed_values[0]",
                "Demo -> notebook-dolphin-bridge -> DolphinScheduler -> original script",
                "public_path: \"/data-platform/notebook-dolphin-bridge/api/v1/runs\"",
                "upstream_path: \"/api/v1/runs\"",
                "direct_api_call: false",
                "/executors/start-workflow-instance",
                "workflowDefinitionCode",
                "dolphinSchedulerStartParams(run)",
                "startParams: {}",
                "类型 1：DolphinScheduler startParams 为空",
                "Object.assign({ run_id: run.run_id }, parameters, { run_id: run.run_id })",
                "类型 2/3：DolphinScheduler startParams 包含 run_id 和直接业务参数",
                "workflow_instance_id",
                "task_instance_id",
                "bridge_accept_response",
                "bridge_query_response",
                "state.executionEvidence.run_id !== run.run_id");
        assertThat(html).doesNotContain("<textarea id=\"run-parameters\"");
        assertThat(script).doesNotContain(
                "/tasks", "task_key", "tsk_", "/uploads", "/timeline", "/diagnosis", "/lineage",
                "Authorization", "service_token", "BRIDGE_SERVICE_TOKEN", "DolphinSchedulerClient",
                "http://60.", "https://60.");
        assertThat(legacyScript)
                .contains("etl-console.html?v=20260722-bridge-v1", "window.location.replace")
                .doesNotContain("/api/bridge", "/api/v1", "service_token");
    }

    @Test
    void consoleKeepsSchedulerObjectsInCollapsedTechnicalDetails() throws IOException {
        String html = resource("static/etl-console.html");
        String script = resource("static/bridge-console/app.js");

        assertThat(html).contains("<details>", "调度 Workflow / Task 实例技术详情");
        assertThat(script).contains("scheduler: entry.scheduler", "scheduler: run.scheduler");
        assertThat(script).contains("entry.queue?.position", "queue.queue?.position");
        assertThat(script).contains("businessRun(current.latest_run)", "businessRun(current.running_run)");
        assertThat(script).doesNotContain("pretty(current)");
        assertThat(script).contains("response.total ?? response.count");
    }

    @Test
    void consoleNeverCallsDolphinSchedulerDirectlyAndUsesBridgeContractDefaults() throws IOException {
        String script = resource("static/bridge-console/app.js");

        assertThat(script)
                .contains("api(\"/runs\"", "automatic.values", "entry?.parameters", "spec.default")
                .doesNotContain("fetch(\"/api/v1", "dolphinscheduler/api", "DOLPHINSCHEDULER_TOKEN",
                        "dp_task_params_b64", "decoded_parameters");
    }

    private static String resource(String name) throws IOException {
        try (var input = BridgeConsoleAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
