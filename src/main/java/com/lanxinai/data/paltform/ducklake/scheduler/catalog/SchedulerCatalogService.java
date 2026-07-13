package com.lanxinai.data.paltform.ducklake.scheduler.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedNode;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedProject;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedTaskGroup;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ManagedWorkflow;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.ExecutionDefaults;
import com.lanxinai.data.paltform.ducklake.scheduler.config.SchedulerProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class SchedulerCatalogService {

    private static final Pattern LOGICAL_ID = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]*$");

    private final SchedulerProperties properties;
    private final ObjectMapper mapper;

    public SchedulerCatalogService(SchedulerProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
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

    private static void validate(SchedulerCatalog catalog) {
        if (catalog == null || catalog.schemaVersion() != 1 || catalog.execution() == null
                || catalog.projects() == null) {
            throw new IllegalStateException("Scheduler catalog schemaVersion must be 1");
        }
        validateExecution(catalog.execution());
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

    private static void validateWorkflows(ManagedProject project) {
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
                requirePositive(node.code(), "node code");
                if (!nodeIds.add(node.id())) {
                    throw new IllegalStateException("Duplicate managed node: " + node.id());
                }
            }
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
