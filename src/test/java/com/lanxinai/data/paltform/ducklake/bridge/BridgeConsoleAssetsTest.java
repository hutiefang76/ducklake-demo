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
        String styles = resource("static/bridge-console/styles.css");
        String legacyScript = resource("static/etl-console.js");
        String application = resource("application.yml");

        assertThat(html).contains("源码分支与扫描", "扫描详情", "全局队列", "停止执行", "重试执行",
                "scan-refresh", "run-refresh", "name=\"support_level\"", "name=\"runnable\"",
                "id=\"scan-ref\"", "切换并扫描", "role=\"treegrid\"", "id=\"script-rows\"",
                "原生 Python", "自动参数", "完整 ETL 契约", "辅助文件",
                "Bridge 脚本 ID", "Bridge Run ID", "此脚本的执行记录",
                "Demo → Bridge → DolphinScheduler 调用证据",
                "上传输入文件", "run-file", "file-upload", "run-retry", "runnable-basis");
        assertThat(html).doesNotContain("类型 1 ·", "类型 2 ·", "类型 3 ·", "<th>短 ID</th>", "<h2>执行历史</h2>");
        assertThat(styles).contains(".script-tree", ".tree-folder", ".script-inline-detail",
                ".run-inline-detail", ".runnable-basis", ".panel-parking");
        assertThat(script).contains(
                "api(\"/scans/options\")",
                "api(\"/scans/latest\")",
                "api(\"/scans\"",
                "JSON.stringify({ repository_ref: repositoryRef })",
                "api(`/scripts?",
                "api(`/runs/current?",
                "api(\"/queue\")",
                "api(\"/files\"",
                "api(`/files/${",
                "/logs?limit=500",
                "/stop`",
                "/retry`",
                "byId(\"run-retry\")",
                "new FormData()",
                "file_ids: fileIds",
                "byId(\"scan-refresh\")",
                "byId(\"run-refresh\")");
        assertThat(script).contains(
                "function automaticParameters(entry)",
                "原生 Python 固定无参数",
                "new URLSearchParams({ all: \"true\", page_size: \"200\" })",
                "query.set(\"support_level\", supportLevel)",
                "query.set(\"runnable\", runnable)",
                "buildScriptTree(scripts)",
                "renderScriptTree(buildScriptTree(scripts)",
                "sourceRow.after(host)",
                "sourceRow.after(row)",
                "Bridge 脚本 ID：",
                "Bridge Run ID：",
                "DolphinScheduler Task Instance ID",
                "Python 语法或编译成功只是前置门槛，不等于可执行",
                "role=task", "adapter", "BOUND",
                "扫描文件", "已入库", "需处理",
                "spec.allowed_values[0]",
                "Demo -> notebook-dolphin-bridge -> DolphinScheduler -> original script",
                "public_path: \"/data-platform/notebook-dolphin-bridge/api/v1/runs\"",
                "upstream_path: \"/api/v1/runs\"",
                "direct_api_call: false",
                "/executors/start-workflow-instance",
                "workflowDefinitionCode",
                "dolphinSchedulerStartParams(run)",
                "startParams: {}",
                "原生 Python：DolphinScheduler startParams 为空",
                "Object.assign({ run_id: run.run_id }, parameters, { run_id: run.run_id })",
                "自动参数/完整 ETL 契约：DolphinScheduler startParams 包含 run_id 和直接业务参数",
                "workflow_instance_id",
                "task_instance_id",
                "bridge_accept_response",
                "bridge_query_response",
                "state.executionEvidence.run_id !== run.run_id");
        assertThat(html).doesNotContain("<textarea id=\"run-parameters\"");
        assertThat(script).doesNotContain(
                "/tasks", "task_key", "tsk_", "/uploads", "/timeline", "/diagnosis", "/lineage", "file_ids: []",
                "Authorization", "service_token", "BRIDGE_SERVICE_TOKEN", "DolphinSchedulerClient",
                "http://60.", "https://60.", "类型 1", "类型 2/3");
        assertThat(application).contains(
                "max-file-size: ${BRIDGE_UPLOAD_MAX_SIZE:100MB}",
                "max-request-size: ${BRIDGE_UPLOAD_MAX_SIZE:100MB}");
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
