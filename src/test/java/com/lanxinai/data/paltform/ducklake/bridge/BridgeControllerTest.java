package com.lanxinai.data.paltform.ducklake.bridge;

import tools.jackson.databind.ObjectMapper;
import com.lanxinai.data.paltform.ducklake.controller.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BridgeControllerTest {

    private BridgeClient client;
    private MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        client = mock(BridgeClient.class);
        BridgeProperties properties = new BridgeProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://bridge.internal:8080");
        properties.setServiceToken("fixture-credential");
        mvc = MockMvcBuilders.standaloneSetup(new BridgeController(client, properties))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void exposesSafeStatusWithoutConnectionOrCredential() throws Exception {
        when(client.get("/api/v1/scripts?page=1&page_size=1")).thenReturn(new BridgeClient.BridgeResponse(200,
                mapper.readTree("{\"items\":[{\"script_id\":\"fixture\"}],\"total\":1}")));

        mvc.perform(get("/api/bridge/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.service_ready").value(true))
                .andExpect(jsonPath("$.acceptance_ready").value(true))
                .andExpect(jsonPath("$.script_count").value(1))
                .andExpect(jsonPath("$.upstream_status").value(200))
                .andExpect(jsonPath("$.contract").value("fresh-v1"))
                .andExpect(jsonPath("$.base_url").doesNotExist())
                .andExpect(jsonPath("$.service_token").doesNotExist());
        mvc.perform(get("/api/bridge/readiness"))
                .andExpect(status().isOk());
    }

    @Test
    void keepsUiReachableBeforeFirstScanWithoutClaimingAcceptanceReady() throws Exception {
        when(client.get("/api/v1/scripts?page=1&page_size=1")).thenReturn(new BridgeClient.BridgeResponse(200,
                mapper.readTree("{\"items\":[],\"total\":0}")));

        mvc.perform(get("/api/bridge/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service_ready").value(true))
                .andExpect(jsonPath("$.acceptance_ready").value(false))
                .andExpect(jsonPath("$.script_count").value(0));
        mvc.perform(get("/api/bridge/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptance_ready").value(false));
    }

    @Test
    void reportsFreshContractMismatchWithoutExposingUpstreamDetails() throws Exception {
        when(client.get("/api/v1/scripts?page=1&page_size=1")).thenReturn(new BridgeClient.BridgeResponse(404,
                mapper.readTree("{\"code\":\"NOT_FOUND\",\"internal\":\"must-not-leak\"}")));

        mvc.perform(get("/api/bridge/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.service_ready").value(false))
                .andExpect(jsonPath("$.acceptance_ready").value(false))
                .andExpect(jsonPath("$.upstream_status").value(404))
                .andExpect(jsonPath("$.internal").doesNotExist());
        mvc.perform(get("/api/bridge/readiness"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void forwardsBranchOptionsOnlyThroughBridge() throws Exception {
        when(client.get("/api/v1/scans/options")).thenReturn(new BridgeClient.BridgeResponse(200,
                mapper.readTree("""
                        {"default_repository_ref":"refs/heads/main",
                         "available_repository_refs":["refs/heads/main","refs/heads/codex/showcase"],
                         "active_repository_ref":"refs/heads/main"}
                        """)));

        mvc.perform(get("/api/bridge/scans/options"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.default_repository_ref").value("refs/heads/main"));
        verify(client).get("/api/v1/scans/options");
    }

    @Test
    void forwardsOnlyAllowedScriptAndRunPagination() throws Exception {
        when(client.get(anyString())).thenReturn(new BridgeClient.BridgeResponse(200,
                mapper.readTree("{\"items\":[],\"total\":0}")));

        mvc.perform(get("/api/bridge/scripts")
                        .param("folder_prefix", "mdm_etl")
                        .param("recursive", "true")
                        .param("support_level", "PARAMETERIZED")
                        .param("runnable", "true")
                        .param("page", "2")
                        .param("page_size", "25"))
                .andExpect(status().isOk());
        verify(client).get("/api/v1/scripts?folder_prefix=mdm_etl&recursive=true&support_level=PARAMETERIZED&runnable=true&page=2&page_size=25");

        mvc.perform(get("/api/bridge/runs")
                        .param("script_id", "scr_orders_0123456789abcdfg")
                        .param("page", "3")
                        .param("page_size", "25"))
                .andExpect(status().isOk());
        verify(client).get("/api/v1/runs?script_id=scr_orders_0123456789abcdfg&page=3&page_size=25");
    }

    @Test
    void rejectsLegacyOrArbitraryQuerySemantics() throws Exception {
        mvc.perform(get("/api/bridge/runs").param("limit", "100"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/bridge/scripts").param("task_key", "legacy"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forwardsMultipartUploadAndFileStatusWithoutReturningCredential() throws Exception {
        String fileId = "fil_01ARZ3NDEKTSV4RRFFQ69G5FAV";
        when(client.upload(eq("/api/v1/files"), any(MultipartFile.class)))
                .thenReturn(new BridgeClient.BridgeResponse(201,
                        mapper.readTree("{\"file_id\":\"" + fileId
                                + "\",\"original_name\":\"input_excel.xlsx\",\"status\":\"AVAILABLE\"}")));
        when(client.get("/api/v1/files/" + fileId))
                .thenReturn(new BridgeClient.BridgeResponse(200,
                        mapper.readTree("{\"file_id\":\"" + fileId
                                + "\",\"original_name\":\"input_excel.xlsx\",\"status\":\"AVAILABLE\"}")));
        MockMultipartFile file = new MockMultipartFile(
                "file", "input_excel.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "xlsx-fixture-content".getBytes());

        mvc.perform(multipart("/api/bridge/files").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.file_id").value(fileId))
                .andExpect(jsonPath("$.status").value("AVAILABLE"))
                .andExpect(jsonPath("$.service_token").doesNotExist());
        mvc.perform(get("/api/bridge/files/" + fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file_id").value(fileId));

        verify(client).upload(eq("/api/v1/files"), any(MultipartFile.class));
        verify(client).get("/api/v1/files/" + fileId);
    }
    // 必须经 Spring MVC 真实反序列化 POST JSON，防止 Jackson 2/3 类型混用再次变成 500。

    @Test
    void forwardsRunCreationWithFilesStopRetryQueueAndLogs() throws Exception {
        when(client.post(anyString(), any(), any())).thenReturn(new BridgeClient.BridgeResponse(202,
                mapper.readTree("{\"run_id\":\"run_01ARZ3NDEKTSV4RRFFQ69G5FAV\",\"state\":\"QUEUED\"}")));
        when(client.get(anyString())).thenReturn(new BridgeClient.BridgeResponse(200, mapper.createObjectNode()));

        mvc.perform(post("/api/bridge/runs")
                        .header("Idempotency-Key", "run-with-file")
                        .contentType("application/json")
                        .content("{\"script_id\":\"scr_orders_0123456789abcdfg\",\"parameters\":{},"
                                + "\"file_ids\":[\"fil_01ARZ3NDEKTSV4RRFFQ69G5FAV\"]}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/bridge/runs/run_01ARZ3NDEKTSV4RRFFQ69G5FAV/stop")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/bridge/runs/run_01ARZ3NDEKTSV4RRFFQ69G5FAV/retry")
                        .header("Idempotency-Key", "retry-fixture"))
                .andExpect(status().isAccepted());
        verify(client).post(eq("/api/v1/runs/run_01ARZ3NDEKTSV4RRFFQ69G5FAV/retry"),
                isNull(), eq("retry-fixture"));
        mvc.perform(get("/api/bridge/queue/run_01ARZ3NDEKTSV4RRFFQ69G5FAV"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/bridge/runs/run_01ARZ3NDEKTSV4RRFFQ69G5FAV/logs").param("limit", "500"))
                .andExpect(status().isOk());

        verify(client).post(eq("/api/v1/runs"),
                org.mockito.ArgumentMatchers.argThat(body -> body.path("file_ids").size() == 1
                        && body.path("file_ids").get(0).asText()
                        .equals("fil_01ARZ3NDEKTSV4RRFFQ69G5FAV")),
                eq("run-with-file"));
    }
}
