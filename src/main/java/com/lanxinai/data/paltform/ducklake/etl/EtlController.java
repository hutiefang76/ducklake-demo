package com.lanxinai.data.paltform.ducklake.etl;

import com.lanxinai.data.paltform.ducklake.etl.EtlLedgerRepository.ArtifactRecord;
import com.lanxinai.data.paltform.ducklake.etl.EtlOrchestrationService.GenericRunRequest;
import com.lanxinai.data.paltform.ducklake.etl.EtlOrchestrationService.MaterialRefreshRequest;
import com.lanxinai.data.paltform.ducklake.etl.EtlOrchestrationService.SubmittedRun;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "ETL business API")
public class EtlController {

    private final EtlArtifactService artifacts;
    private final EtlOrchestrationService orchestration;

    public EtlController(EtlArtifactService artifacts, EtlOrchestrationService orchestration) {
        this.artifacts = artifacts;
        this.orchestration = orchestration;
    }

    @PostMapping(value = "/etl/artifacts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "流式上传 Excel/Parquet/CSV 到 SeaweedFS S3")
    public ArtifactRecord upload(
            @RequestParam MultipartFile file,
            @RequestHeader(value = "X-Requested-By", defaultValue = "demo-user") String requestedBy) {
        return artifacts.upload(file, requestedBy);
    }

    @PostMapping("/material-master/refresh")
    @Operation(summary = "以业务术语执行完整物料主数据刷新 Workflow")
    public SubmittedRun materialRefresh(
            @RequestBody MaterialRefreshRequest request,
            @RequestHeader(value = "X-Requested-By", defaultValue = "demo-user") String requestedBy) {
        return orchestration.startMaterialRefresh(request, requestedBy);
    }

    @PostMapping("/etl/projects/{projectId}/workflows/{workflowId}/runs")
    @Operation(summary = "通过逻辑 ID 执行任意受管 Workflow")
    public SubmittedRun startWorkflow(
            @PathVariable String projectId,
            @PathVariable String workflowId,
            @RequestBody GenericRunRequest request,
            @RequestHeader(value = "X-Requested-By", defaultValue = "demo-user") String requestedBy) {
        return orchestration.start(
                projectId, workflowId, request == null ? null : request.parameters(),
                requestedBy, request == null ? null : request.reason());
    }
}
