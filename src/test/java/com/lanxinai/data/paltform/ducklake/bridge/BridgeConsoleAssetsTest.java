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

        assertThat(html).contains("script_name", "script_id", "源码扫描", "全局队列", "执行历史", "停止执行",
                "scan-refresh", "run-refresh");
        assertThat(script).contains(
                "api(\"/scans/latest\")",
                "api(\"/scans\"",
                "api(`/scripts?",
                "api(`/runs/current?",
                "api(\"/queue\")",
                "/logs?limit=500",
                "/stop`",
                "byId(\"scan-refresh\")",
                "byId(\"run-refresh\")");
        assertThat(script).doesNotContain(
                "/tasks", "task_key", "tsk_", "/uploads", "/timeline", "/diagnosis", "/lineage",
                "Authorization", "service_token", "BRIDGE_SERVICE_TOKEN", "DolphinSchedulerClient",
                "http://60.", "https://60.", "/api/v1");
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

    private static String resource(String name) throws IOException {
        try (var input = BridgeConsoleAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
