package com.lanxinai.data.paltform.ducklake.scheduler.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedNode;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedProject;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedTaskGroup;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedWorkflow;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ExecutionDefaults;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.OperationResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.QueueItem;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.QueueResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunLogResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunStatusResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunTasksResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.TaskInstanceSummary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class DolphinSchedulerClient {

    private final SchedulerProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;
    private final AtomicReference<String> sessionId = new AtomicReference<>();

    public DolphinSchedulerClient(
            SchedulerProperties properties,
            ObjectMapper mapper,
            HttpClient dolphinSchedulerHttpClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = dolphinSchedulerHttpClient;
    }

    public RunResponse startRun(
            ExecutionDefaults execution,
            ManagedProject project,
            ManagedWorkflow workflow,
            ManagedNode node,
            RunManifestRef manifest) {
        return start(execution, project, workflow, node, manifest);
    }

    public RunResponse startWorkflow(
            ExecutionDefaults execution,
            ManagedProject project,
            ManagedWorkflow workflow,
            RunManifestRef manifest) {
        return start(execution, project, workflow, null, manifest);
    }

    private RunResponse start(
            ExecutionDefaults execution,
            ManagedProject project,
            ManagedWorkflow workflow,
            ManagedNode node,
            RunManifestRef manifest) {
        if (manifest == null || manifest.uri() == null || manifest.uri().isBlank()
                || manifest.sha256() == null || manifest.sha256().isBlank()) {
            throw new IllegalArgumentException("run manifest URI and SHA-256 are required");
        }
        List<Map<String, String>> startParams = List.of(
                Map.of("prop", workflow.runManifestParameter(), "direct", "IN", "type", "VARCHAR", "value", manifest.uri()),
                Map.of("prop", workflow.runManifestSha256Parameter(), "direct", "IN", "type", "VARCHAR", "value", manifest.sha256()));

        Map<String, String> form = new LinkedHashMap<>();
        form.put("workflowDefinitionCode", Long.toString(workflow.code()));
        form.put("tenantCode", execution.tenantCode());
        form.put("scheduleTime", "");
        form.put("failureStrategy", execution.failureStrategy());
        form.put("warningType", execution.warningType());
        form.put("warningGroupId", Integer.toString(execution.warningGroupId()));
        form.put("execType", "START_PROCESS");
        if (node != null) {
            form.put("startNodeList", Long.toString(node.code()));
        }
        form.put("taskDependType", "TASK_POST");
        form.put("runMode", execution.runMode());
        form.put("workflowInstancePriority", execution.workflowInstancePriority());
        form.put("workerGroup", execution.workerGroup());
        form.put("environmentCode", Long.toString(execution.environmentCode()));
        form.put("startParams", writeJson(startParams));
        form.put("dryRun", "0");
        form.put("testFlag", "0");

        JsonNode response = request(
                "POST",
                projectPath(project, "/executors/start-workflow-instance"),
                form);
        Long instanceId = extractId(response.path("data"));
        if (instanceId == null || instanceId <= 0) {
            throw new SchedulerClientException(
                    "DolphinScheduler accepted the run but returned no workflow instance id");
        }
        return new RunResponse(
                project.id(), workflow.id(), node == null ? null : node.id(),
                node == null ? null : node.taskId(), instanceId);
    }

    public RunStatusResponse runStatus(ManagedProject project, long instanceId) {
        requirePositive(instanceId, "workflowInstanceId");
        JsonNode data = request(
                "GET",
                projectPath(project, "/workflow-instances/" + instanceId),
                null).path("data");
        return new RunStatusResponse(
                project.id(),
                instanceId,
                data.path("name").asText(""),
                data.path("state").asText("UNKNOWN"),
                textOrNull(data, "submitTime"),
                textOrNull(data, "startTime"),
                textOrNull(data, "endTime"),
                false,
                false,
                null,
                null);
    }

    public RunTasksResponse runTasks(ManagedProject project, long instanceId) {
        requirePositive(instanceId, "workflowInstanceId");
        JsonNode data = request(
                "GET",
                projectPath(project, "/workflow-instances/" + instanceId + "/tasks?pageNo=1&pageSize=100"),
                null).path("data");
        JsonNode taskList = data.path("taskList");
        if (!taskList.isArray()) {
            taskList = data.path("totalList");
        }
        List<TaskInstanceSummary> tasks = new ArrayList<>();
        if (taskList.isArray()) {
            taskList.forEach(task -> tasks.add(new TaskInstanceSummary(
                    task.path("id").asLong(),
                    task.path("name").asText(""),
                    task.path("taskCode").asLong(task.path("code").asLong()),
                    task.path("taskType").asText(""),
                    task.path("state").asText("UNKNOWN"),
                    textOrNull(task, "submitTime"),
                    textOrNull(task, "startTime"),
                    textOrNull(task, "endTime"))));
        }
        return new RunTasksResponse(project.id(), instanceId, List.copyOf(tasks));
    }

    public RunLogResponse runLog(
            ManagedProject project,
            long instanceId,
            Long taskInstanceId,
            int skipLineNum,
            int limit) {
        if (skipLineNum < 0 || limit < 1 || limit > 100_000) {
            throw new IllegalArgumentException("Invalid log range");
        }
        RunTasksResponse taskResponse = runTasks(project, instanceId);
        long resolvedTaskId = resolveTaskInstanceId(taskResponse, taskInstanceId);
        JsonNode data = request(
                "GET",
                "/log/detail?taskInstanceId=" + resolvedTaskId
                        + "&skipLineNum=" + skipLineNum + "&limit=" + limit,
                null).path("data");
        String message = data.isObject() ? data.path("message").asText("") : data.asText("");
        return new RunLogResponse(
                project.id(), instanceId, resolvedTaskId, skipLineNum, limit, message);
    }

    public OperationResponse stopRun(ManagedProject project, long instanceId) {
        requirePositive(instanceId, "workflowInstanceId");
        request(
                "POST",
                projectPath(project, "/executors/execute"),
                Map.of(
                        "workflowInstanceId", Long.toString(instanceId),
                        "executeType", "STOP"));
        return new OperationResponse(
                "STOP",
                project.id(),
                instanceId,
                true,
                "COMMAND_ACCEPTED",
                true,
                "/api/scheduler/projects/" + project.id() + "/runs/" + instanceId + "/status",
                "DolphinScheduler accepted the command; poll status until terminal state");
    }

    public QueueResponse taskGroupQueue(
            ManagedProject project,
            ManagedTaskGroup taskGroup,
            Integer status,
            int pageNo,
            int pageSize) {
        if (pageNo < 1 || pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageNo must be positive and pageSize must be between 1 and 100");
        }
        String path = "/task-group/query-list-by-group-id?groupId=" + taskGroup.groupId()
                + "&pageNo=" + pageNo + "&pageSize=" + pageSize;
        if (status != null) {
            path += "&status=" + status;
        }
        JsonNode data = request("GET", path, null).path("data");
        JsonNode totalList = data.path("totalList");
        List<QueueItem> items = new ArrayList<>();
        if (totalList.isArray()) {
            for (int index = 0; index < totalList.size(); index++) {
                JsonNode item = totalList.get(index);
                items.add(new QueueItem(
                        (pageNo - 1) * pageSize + index + 1,
                        item.path("id").asInt(),
                        item.path("taskId").asInt(),
                        item.path("taskName").asText(""),
                        item.path("workflowInstanceId").asInt(),
                        item.path("workflowInstanceName").asText(""),
                        item.path("priority").asInt(),
                        item.path("status").asText("UNKNOWN"),
                        textOrNull(item, "createTime"),
                        textOrNull(item, "updateTime")));
            }
        }
        return new QueueResponse(
                project.id(),
                taskGroup.id(),
                taskGroup.name(),
                data.path("total").asInt(items.size()),
                false,
                "DolphinScheduler API snapshot order; not an authoritative execution position",
                List.copyOf(items));
    }

    private long resolveTaskInstanceId(RunTasksResponse response, Long requestedId) {
        if (requestedId == null) {
            if (response.tasks().isEmpty()) {
                throw new IllegalStateException("DolphinScheduler task instance is not available yet");
            }
            return response.tasks().get(0).id();
        }
        requirePositive(requestedId, "taskInstanceId");
        if (response.tasks().stream().noneMatch(task -> task.id() == requestedId)) {
            throw new IllegalArgumentException(
                    "taskInstanceId does not belong to the requested workflow instance");
        }
        return requestedId;
    }

    private JsonNode request(String method, String path, Map<String, String> form) {
        properties.requireAvailable();
        try {
            RawResponse raw = doRequest(method, path, form);
            if (!properties.hasToken() && isUnauthorized(raw)) {
                sessionId.set(null);
                raw = doRequest(method, path, form);
            }
            validateResponse(raw);
            return raw.body();
        } catch (IOException exception) {
            throw new SchedulerClientException("DolphinScheduler request failed due to an IO error", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new SchedulerClientException("DolphinScheduler request was interrupted", exception);
        }
    }

    private RawResponse doRequest(String method, String path, Map<String, String> form)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + path))
                .timeout(properties.getRequestTimeout());
        if (properties.hasToken()) {
            builder.header("token", properties.getToken());
        } else {
            builder.header("sessionId", ensureSession());
        }
        if (form == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/x-www-form-urlencoded");
            builder.method(method, HttpRequest.BodyPublishers.ofString(encodeForm(form)));
        }
        HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
        return new RawResponse(response.statusCode(), readBody(response.body()));
    }

    private String ensureSession() throws IOException, InterruptedException {
        String current = sessionId.get();
        if (current != null && !current.isBlank()) {
            return current;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + "/login"))
                .timeout(properties.getRequestTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(Map.of(
                        "userName", properties.getUsername(),
                        "userPassword", properties.getPassword()))))
                .build();
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());
        RawResponse raw = new RawResponse(response.statusCode(), readBody(response.body()));
        validateResponse(raw);
        String resolved = raw.body().path("data").path("sessionId").asText("");
        if (resolved.isBlank()) {
            throw new SchedulerClientException("DolphinScheduler login returned no session id");
        }
        sessionId.compareAndSet(null, resolved);
        return sessionId.get();
    }

    private JsonNode readBody(String body) throws JsonProcessingException {
        return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
    }

    private void validateResponse(RawResponse response) {
        JsonNode body = response.body();
        int code = body.path("code").asInt(Integer.MIN_VALUE);
        boolean success = body.path("success").asBoolean(true);
        if (response.statusCode() < 200 || response.statusCode() >= 300 || code != 0 || !success) {
            String message = body.path("msg").asText(body.path("message").asText("upstream rejected request"));
            throw new SchedulerClientException(
                    "DolphinScheduler API failed (http=" + response.statusCode()
                            + ", code=" + code + ", message=" + abbreviate(message) + ")");
        }
    }

    private static String projectPath(ManagedProject project, String suffix) {
        return "/projects/" + project.code() + suffix;
    }

    private String writeJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Run params must be JSON serializable", exception);
        }
    }

    private static String encodeForm(Map<String, String> form) {
        return form.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static Long extractId(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isIntegralNumber()) {
            return node.asLong();
        }
        if (node.isTextual() && node.asText().matches("\\d+")) {
            return Long.parseLong(node.asText());
        }
        if (node.isArray() && !node.isEmpty()) {
            return extractId(node.get(0));
        }
        if (node.isObject()) {
            for (String field : List.of("id", "workflowInstanceId", "processInstanceId")) {
                Long value = extractId(node.path(field));
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() || value.asText().isBlank()
                ? null : value.asText();
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }

    private static boolean isUnauthorized(RawResponse response) {
        return response.statusCode() == 401 || response.body().path("code").asInt() == 401;
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String abbreviate(String value) {
        return value.length() <= 240 ? value : value.substring(0, 240);
    }

    private record RawResponse(int statusCode, JsonNode body) {
    }
}
