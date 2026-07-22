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

        assertThat(html).contains("script_name", "script_id", "源码扫描", "全局队列", "执行历史", "停止执行");
        assertThat(script).contains(
                "api(\"/scans/latest\")",
                "api(\"/scans\"",
                "api(`/scripts?",
                "api(`/runs/current?",
                "api(\"/queue\")",
                "/logs?limit=500",
                "/stop`");
        assertThat(script).doesNotContain(
                "/tasks", "task_key", "tsk_", "/uploads", "/timeline", "/diagnosis", "/lineage",
                "Authorization", "service_token", "BRIDGE_SERVICE_TOKEN", "DolphinSchedulerClient");
    }

    @Test
    void consoleKeepsSchedulerObjectsInCollapsedTechnicalDetails() throws IOException {
        String html = resource("static/etl-console.html");
        String script = resource("static/bridge-console/app.js");

        assertThat(html).contains("<details>", "调度 Workflow / Task 实例技术详情");
        assertThat(script).contains("scheduler: entry.scheduler", "scheduler: run.scheduler");
        assertThat(script).contains("response.total ?? response.count");
    }

    private static String resource(String name) throws IOException {
        try (var input = BridgeConsoleAssetsTest.class.getClassLoader().getResourceAsStream(name)) {
            assertThat(input).as(name).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
