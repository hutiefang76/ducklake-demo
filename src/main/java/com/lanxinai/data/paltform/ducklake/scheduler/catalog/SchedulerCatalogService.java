package com.lanxinai.data.paltform.ducklake.scheduler.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedNode;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedProject;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedTaskGroup;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedWorkflow;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ExecutionDefaults;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.LineageCatalog;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.TaskContract;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SchedulerCatalogService {

    private static final Pattern LOGICAL_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private final SchedulerProperties properties;
    private final ObjectMapper mapper;
    private final ObjectMapper canonicalMapper;

    public SchedulerCatalogService(SchedulerProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.canonicalMapper = mapper.copy()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public SchedulerCatalog load() {
        properties.requireAvailable();
        Path path = properties.getCatalogPath().toAbsolutePath().normalize();
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Scheduler catalog does not exist: " + path);
        }
        try {
            SchedulerCatalog catalog = mapper.readValue(path.toFile(), SchedulerCatalog.class);
            validate(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read scheduler catalog: " + path, exception);
        }
    }

    public ResolvedRun resolveRun(String projectId, String workflowId, String nodeId) {
        SchedulerCatalog catalog = load();
        ManagedProject project = project(catalog, projectId);
        ManagedWorkflow workflow = workflow(project, workflowId);
        ManagedNode node = node(workflow, nodeId);
        return new ResolvedRun(catalog.execution(), project, workflow, node);
    }

    public ResolvedWorkflow resolveWorkflow(String projectId, String workflowId) {
        SchedulerCatalog catalog = load();
        ManagedProject project = project(catalog, projectId);
        return new ResolvedWorkflow(catalog.execution(), project, workflow(project, workflowId));
    }

    public ResolvedTaskGroup resolveTaskGroup(String projectId, String taskGroupId) {
        SchedulerCatalog catalog = load();
        ManagedProject project = project(catalog, projectId);
        return new ResolvedTaskGroup(project, taskGroup(project, taskGroupId));
    }

    public ManagedProject project(String projectId) {
        return project(load(), projectId);
    }

    public TaskContract task(String taskId) {
        SchedulerCatalog catalog = load();
        return catalog.tasks().stream()
                .filter(task -> task.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown managed task: " + taskId));
    }

    public LineageCatalog lineage() {
        return load().lineage();
    }

    public Map<String, Object> taskLineage(String taskId) {
        SchedulerCatalog catalog = load();
        TaskContract task = catalog.tasks().stream()
                .filter(item -> item.id().equals(taskId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown managed task: " + taskId));
        List<Map<String, Object>> edges = catalog.lineage().taskEdges().stream()
                .filter(item -> taskId.equals(item.get("taskId")))
                .toList();
        List<Map<String, Object>> transformations = catalog.lineage().transformations().stream()
                .filter(item -> taskId.equals(item.get("taskId")))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("task", task);
        result.put("taskEdges", edges);
        result.put("transformations", transformations);
        return java.util.Collections.unmodifiableMap(result);
    }

    private static ManagedProject project(SchedulerCatalog catalog, String projectId) {
        return catalog.projects().stream()
                .filter(project -> project.id().equals(projectId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown managed project: " + projectId));
    }

    public ManagedWorkflow workflow(ManagedProject project, String workflowId) {
        return project.workflows().stream()
                .filter(workflow -> workflow.id().equals(workflowId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown managed workflow: " + project.id() + "/" + workflowId));
    }

    public ManagedNode node(ManagedWorkflow workflow, String nodeId) {
        return workflow.nodes().stream()
                .filter(node -> node.id().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown managed node: " + workflow.id() + "/" + nodeId));
    }

    public ManagedTaskGroup taskGroup(ManagedProject project, String taskGroupId) {
        return project.taskGroups().stream()
                .filter(group -> group.id().equals(taskGroupId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown managed task group: " + project.id() + "/" + taskGroupId));
    }

    private void validate(SchedulerCatalog catalog) {
        if (catalog == null || catalog.schemaVersion() != 2 || catalog.execution() == null
                || catalog.tasks() == null || catalog.lineage() == null || catalog.projects() == null) {
            throw new IllegalStateException("Scheduler catalog schemaVersion must be 2");
        }
        validateExecution(catalog.execution());
        validateTasksAndLineage(catalog);
        Set<String> projectIds = new HashSet<>();
        for (ManagedProject project : catalog.projects()) {
            requireLogicalId(project.id(), "project id");
            requireId(project.name(), "project name");
            requirePositive(project.code(), "project code");
            if (!projectIds.add(project.id())) {
                throw new IllegalStateException("Duplicate managed project: " + project.id());
            }
            if (project.workflows() == null || project.taskGroups() == null) {
                throw new IllegalStateException("Project workflows/taskGroups must be arrays: " + project.id());
            }
            validateWorkflows(project);
            validateTaskGroups(project);
        }
    }

    private static void validateTasksAndLineage(SchedulerCatalog catalog) {
        Set<String> taskIds = new HashSet<>();
        for (TaskContract task : catalog.tasks()) {
            requireLogicalId(task.id(), "task id");
            requireId(task.title(), "task title");
            requireId(task.description(), "task description");
            requireId(task.path(), "task path");
            if (!"run_etl".equals(task.entrypoint()) || task.parameters() == null
                    || task.data_contract() == null || task.execution() == null) {
                throw new IllegalStateException("Invalid task contract: " + task.id());
            }
            if (!taskIds.add(task.id())) {
                throw new IllegalStateException("Duplicate managed task: " + task.id());
            }
        }
        LineageCatalog lineage = catalog.lineage();
        if (lineage.schemaVersion() != 1 || lineage.datasets() == null
                || lineage.taskEdges() == null || lineage.transformations() == null
                || lineage.workflowEdges() == null) {
            throw new IllegalStateException("Lineage catalog schemaVersion must be 1");
        }
    }

    private static void validateExecution(ExecutionDefaults execution) {
        requireId(execution.tenantCode(), "execution tenantCode");
        requireId(execution.workerGroup(), "execution workerGroup");
        requireId(execution.failureStrategy(), "execution failureStrategy");
        requireId(execution.warningType(), "execution warningType");
        requireId(execution.runMode(), "execution runMode");
        requireId(execution.workflowInstancePriority(), "execution workflowInstancePriority");
        if (execution.warningGroupId() < 0) {
            throw new IllegalStateException("execution warningGroupId must not be negative");
        }
        if (execution.environmentCode() < -1 || execution.environmentCode() == 0) {
            throw new IllegalStateException("execution environmentCode must be -1 or positive");
        }
    }

    private void validateWorkflows(ManagedProject project) {
        Set<String> ids = new HashSet<>();
        for (ManagedWorkflow workflow : project.workflows()) {
            requireLogicalId(workflow.id(), "workflow id");
            requireId(workflow.name(), "workflow name");
            requirePositive(workflow.code(), "workflow code");
            requireLogicalId(workflow.runManifestParameter(), "runManifestParameter");
            requireLogicalId(workflow.runManifestSha256Parameter(), "runManifestSha256Parameter");
            if (workflow.parameterSchema() == null) {
                throw new IllegalStateException("Workflow parameterSchema is required: " + workflow.id());
            }
            if (workflow.parameterSchemaVersion() != 2) {
                throw new IllegalStateException(
                        "Workflow parameterSchemaVersion must be 2: " + workflow.id());
            }
            requireId(workflow.parameterSchemaSha256(), "workflow parameterSchemaSha256");
            String actualSchemaHash = parameterSchemaSha256(
                    workflow.parameterSchemaVersion(), workflow.parameterSchema());
            if (!MessageDigest.isEqual(
                    actualSchemaHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    workflow.parameterSchemaSha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IllegalStateException(
                        "Workflow parameterSchemaSha256 does not match parameterSchema: " + workflow.id());
            }
            if (!ids.add(workflow.id())) {
                throw new IllegalStateException("Duplicate managed workflow: " + workflow.id());
            }
            if (workflow.nodes() == null) {
                throw new IllegalStateException("Workflow nodes must be an array: " + workflow.id());
            }
            Set<String> nodeIds = new HashSet<>();
            for (ManagedNode node : workflow.nodes()) {
                requireLogicalId(node.id(), "node id");
                requireId(node.name(), "node name");
                requireLogicalId(node.taskId(), "task id");
                requireId(node.taskType(), "node taskType");
                requireId(node.title(), "node title");
                requireId(node.description(), "node description");
                if (node.dependsOn() == null || node.parameters() == null || node.dataContract() == null) {
                    throw new IllegalStateException("Node contract fields are required: " + node.id());
                }
                requirePositive(node.code(), "node code");
                if (!nodeIds.add(node.id())) {
                    throw new IllegalStateException("Duplicate managed node: " + node.id());
                }
            }
        }
    }

    /**
     * Hashes the compact canonical JSON object
     * {"parameterSchema":...,"schemaVersion":...}, with every map key sorted.
     */
    public String parameterSchemaSha256(
            int schemaVersion,
            Map<String, Map<String, Object>> parameterSchema) {
        Map<String, Object> versionedSchema = new LinkedHashMap<>();
        versionedSchema.put("schemaVersion", schemaVersion);
        versionedSchema.put("parameterSchema", parameterSchema);
        try {
            byte[] canonicalJson = canonicalMapper.writeValueAsBytes(versionedSchema);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonicalJson));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Unable to canonicalize workflow parameter schema", exception);
        }
    }

    private static void validateTaskGroups(ManagedProject project) {
        Set<String> ids = new HashSet<>();
        for (ManagedTaskGroup group : project.taskGroups()) {
            requireLogicalId(group.id(), "task group id");
            requireId(group.name(), "task group name");
            requirePositive(group.groupId(), "task group numeric id");
            if (!ids.add(group.id())) {
                throw new IllegalStateException("Duplicate managed task group: " + group.id());
            }
        }
    }

    private static void requireId(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(field + " is required");
        }
    }

    private static void requireLogicalId(String value, String field) {
        requireId(value, field);
        if (!LOGICAL_ID.matcher(value).matches()) {
            throw new IllegalStateException(field + " has an invalid logical ID: " + value);
        }
    }

    private static void requirePositive(long value, String field) {
        if (value <= 0) {
            throw new IllegalStateException(field + " must be positive");
        }
    }

    public record ResolvedRun(
            ExecutionDefaults execution,
            ManagedProject project,
            ManagedWorkflow workflow,
            ManagedNode node) {
    }

    public record ResolvedWorkflow(
            ExecutionDefaults execution,
            ManagedProject project,
            ManagedWorkflow workflow) {
    }

    public record ResolvedTaskGroup(
            ManagedProject project,
            ManagedTaskGroup taskGroup) {
    }
}
