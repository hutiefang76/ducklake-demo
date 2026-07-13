package com.lanxinai.data.paltform.ducklake.scheduler.controller;

import com.lanxinai.data.paltform.ducklake.scheduler.SchedulerFacadeService;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.LineageCatalog;
import com.lanxinai.data.paltform.ducklake.scheduler.catalog.SchedulerCatalog.TaskContract;
import java.util.Map;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.OperationResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.ParameterSchemaResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.QueueResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunLogResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunRequest;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunManifestRef;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunStatusResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.RunTasksResponse;
import com.lanxinai.data.paltform.ducklake.scheduler.dto.SchedulerDtos.SchedulerMetaResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scheduler")
@Tag(name = "DolphinScheduler facade")
public class SchedulerController {

    private final SchedulerFacadeService service;

    public SchedulerController(SchedulerFacadeService service) {
        this.service = service;
    }

    @GetMapping("/meta")
    @Operation(summary = "查询安全配置状态")
    public SchedulerMetaResponse meta() {
        return service.meta();
    }

    @GetMapping("/catalog")
    @Operation(summary = "读取转换平台生成的受管 Project/Workflow/Node 目录")
    public SchedulerCatalog catalog() {
        return service.catalog();
    }

    @GetMapping("/lineage")
    @Operation(summary = "查询编译生成的全局数据集、任务和 Workflow 血缘")
    public LineageCatalog lineage() {
        return service.lineage();
    }

    @GetMapping("/tasks/{taskId}/contract")
    @Operation(summary = "查询任务参数、输入、输出、中间数据集和 transformation 契约")
    public TaskContract taskContract(@PathVariable String taskId) {
        return service.taskContract(taskId);
    }

    @GetMapping("/tasks/{taskId}/lineage")
    @Operation(summary = "查询单任务声明血缘")
    public Map<String, Object> taskLineage(@PathVariable String taskId) {
        return service.taskLineage(taskId);
    }

    @GetMapping("/projects/{projectId}/workflows/{workflowId}/parameter-schema")
    @Operation(summary = "读取版本化 ETL 参数 schema 与 canonical SHA-256")
    public ParameterSchemaResponse parameterSchema(
            @PathVariable String projectId,
            @PathVariable String workflowId) {
        return service.parameterSchemaDescriptor(projectId, workflowId);
    }

    @PostMapping("/projects/{projectId}/workflows/{workflowId}/runs")
    @Operation(summary = "按逻辑 Project/Workflow ID 执行完整工作流")
    public RunResponse start(
            @PathVariable String projectId,
            @PathVariable String workflowId,
            @RequestBody RunManifestRef manifest) {
        return service.startWorkflow(projectId, workflowId, manifest);
    }

    @PostMapping("/projects/{projectId}/workflows/{workflowId}/nodes/{nodeId}/runs")
    @Operation(summary = "诊断或补数时执行一个受管节点")
    public RunResponse startNode(
            @PathVariable String projectId,
            @PathVariable String workflowId,
            @PathVariable String nodeId,
            @RequestBody RunManifestRef manifest) {
        return service.startRun(
                projectId,
                workflowId,
                new RunRequest(nodeId, manifest.uri(), manifest.sha256()));
    }

    @GetMapping("/projects/{projectId}/runs/{instanceId}/status")
    public RunStatusResponse status(
            @PathVariable String projectId,
            @PathVariable long instanceId) {
        return service.status(projectId, instanceId);
    }

    @GetMapping("/projects/{projectId}/runs/{instanceId}/tasks")
    public RunTasksResponse tasks(
            @PathVariable String projectId,
            @PathVariable long instanceId) {
        return service.tasks(projectId, instanceId);
    }

    @GetMapping("/projects/{projectId}/runs/{instanceId}/log")
    public RunLogResponse log(
            @PathVariable String projectId,
            @PathVariable long instanceId,
            @RequestParam(required = false) Long taskInstanceId,
            @RequestParam(defaultValue = "0") int skipLineNum,
            @RequestParam(defaultValue = "20000") int limit) {
        return service.log(projectId, instanceId, taskInstanceId, skipLineNum, limit);
    }

    @PostMapping("/projects/{projectId}/runs/{instanceId}/stop")
    public OperationResponse stop(
            @PathVariable String projectId,
            @PathVariable long instanceId) {
        return service.stop(projectId, instanceId);
    }

    @GetMapping("/projects/{projectId}/task-groups/{taskGroupId}/queue")
    @Operation(summary = "读取 TaskGroup 队列快照", description = "snapshotIndex 不是权威执行名次。")
    public QueueResponse queue(
            @PathVariable String projectId,
            @PathVariable String taskGroupId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "20") int pageSize) {
        return service.queue(projectId, taskGroupId, status, pageNo, pageSize);
    }
}
