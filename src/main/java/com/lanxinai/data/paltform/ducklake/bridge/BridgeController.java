package com.lanxinai.data.paltform.ducklake.bridge;

import tools.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/bridge")
@Tag(name = "ETL Bridge 验收", description = "服务端持有 Bridge service token 的最薄验收 BFF")
public class BridgeController {

    private static final Set<String> SCRIPT_QUERY = Set.of(
            "q", "folder_prefix", "recursive", "page", "page_size", "all",
            "support_level", "contract_status", "cli_status", "runnable", "source_path");
    private static final Set<String> RUN_QUERY = Set.of("script_name", "script_id", "state", "page", "page_size");
    private static final Set<String> CURRENT_QUERY = Set.of("script_name", "script_id");
    private static final Pattern SCRIPT_ID = Pattern.compile("scr_[a-z0-9-]+_[0-9a-hjkmnp-tv-z]{16}");
    private static final Pattern RUN_ID = Pattern.compile("run_[0-9A-HJKMNP-TV-Z]{26}");

    private final BridgeClient client;
    private final BridgeProperties properties;

    public BridgeController(BridgeClient client, BridgeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @GetMapping("/status")
    @Operation(summary = "查询 BFF 配置状态", description = "不返回 Bridge 地址或 service token。")
    public Map<String, Object> status() {
        return Map.of(
                "enabled", properties.isEnabled(),
                "configured", properties.isConfigured(),
                "contract", "fresh-v1");
    }

    @PostMapping("/scans")
    @Operation(summary = "触发源码扫描")
    public ResponseEntity<JsonNode> createScan(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return forward(client.post("/api/v1/scans", body, key(idempotencyKey)));
    }

    @GetMapping("/scans/latest")
    @Operation(summary = "查询最新扫描状态")
    public ResponseEntity<JsonNode> latestScan() {
        return forward(client.get("/api/v1/scans/latest"));
    }

    @GetMapping("/scripts")
    @Operation(summary = "分页查询脚本轻列表")
    public ResponseEntity<JsonNode> scripts(@RequestParam MultiValueMap<String, String> query) {
        return forward(client.get(query("/api/v1/scripts", query, SCRIPT_QUERY)));
    }

    @GetMapping("/scripts/by-name")
    @Operation(summary = "按 script_name 查询脚本详情")
    public ResponseEntity<JsonNode> scriptByName(@RequestParam("script_name") String scriptName) {
        return forward(client.get(query("/api/v1/scripts/by-name",
                Map.of("script_name", scriptName))));
    }

    @GetMapping("/scripts/{scriptId}")
    @Operation(summary = "按 script_id 查询脚本详情")
    public ResponseEntity<JsonNode> script(@PathVariable String scriptId) {
        return forward(client.get("/api/v1/scripts/" + require(SCRIPT_ID, scriptId, "script_id")));
    }

    @PostMapping("/runs")
    @Operation(summary = "执行脚本")
    public ResponseEntity<JsonNode> createRun(
            @RequestBody JsonNode body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return forward(client.post("/api/v1/runs", body, key(idempotencyKey)));
    }

    @GetMapping("/runs")
    @Operation(summary = "分页查询执行历史")
    public ResponseEntity<JsonNode> runs(@RequestParam MultiValueMap<String, String> query) {
        return forward(client.get(query("/api/v1/runs", query, RUN_QUERY)));
    }

    @GetMapping("/runs/current")
    @Operation(summary = "查询脚本当前执行状态")
    public ResponseEntity<JsonNode> current(@RequestParam MultiValueMap<String, String> query) {
        return forward(client.get(query("/api/v1/runs/current", query, CURRENT_QUERY)));
    }

    @GetMapping("/runs/{runId}")
    @Operation(summary = "查询执行详情")
    public ResponseEntity<JsonNode> run(@PathVariable String runId) {
        return forward(client.get("/api/v1/runs/" + require(RUN_ID, runId, "run_id")));
    }

    @GetMapping("/runs/{runId}/logs")
    @Operation(summary = "查询执行终态日志")
    public ResponseEntity<JsonNode> logs(
            @PathVariable String runId,
            @RequestParam(defaultValue = "200") int limit) {
        if (limit < 1 || limit > 2000) {
            throw new IllegalArgumentException("limit must be between 1 and 2000");
        }
        return forward(client.get("/api/v1/runs/" + require(RUN_ID, runId, "run_id")
                + "/logs?limit=" + limit));
    }

    @PostMapping("/runs/{runId}/stop")
    @Operation(summary = "停止执行")
    public ResponseEntity<JsonNode> stop(@PathVariable String runId) {
        return forward(client.post("/api/v1/runs/" + require(RUN_ID, runId, "run_id") + "/stop",
                null, null));
    }

    @GetMapping("/queue")
    @Operation(summary = "查询全局排队情况")
    public ResponseEntity<JsonNode> queue() {
        return forward(client.get("/api/v1/queue"));
    }

    @GetMapping("/queue/{runId}")
    @Operation(summary = "查询某次执行的排队状态")
    public ResponseEntity<JsonNode> queueForRun(@PathVariable String runId) {
        return forward(client.get("/api/v1/queue/" + require(RUN_ID, runId, "run_id")));
    }

    private ResponseEntity<JsonNode> forward(BridgeClient.BridgeResponse response) {
        return ResponseEntity.status(response.status()).body(response.body());
    }

    private static String key(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return "demo-" + UUID.randomUUID();
        }
        if (candidate.length() > 255 || candidate.contains("\r") || candidate.contains("\n")) {
            throw new IllegalArgumentException("Invalid Idempotency-Key");
        }
        return candidate;
    }

    private static String require(Pattern pattern, String value, String name) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name);
        }
        return value;
    }

    private static String query(String path, MultiValueMap<String, String> query, Set<String> allowed) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        query.forEach((name, values) -> {
            if (!allowed.contains(name)) {
                throw new IllegalArgumentException("Unsupported query parameter: " + name);
            }
            for (String value : values) {
                if (value != null && value.length() > 2048) {
                    throw new IllegalArgumentException("Query parameter is too long: " + name);
                }
                builder.queryParam(name, value);
            }
        });
        return builder.build().encode().toUriString();
    }

    private static String query(String path, Map<String, String> query) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath(path);
        query.forEach(builder::queryParam);
        return builder.build().encode().toUriString();
    }
}
