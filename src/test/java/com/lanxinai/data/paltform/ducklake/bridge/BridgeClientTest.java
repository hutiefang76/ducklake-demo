package com.lanxinai.data.paltform.ducklake.bridge;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeClientTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void keepsCredentialServerSideAndForwardsBusinessResponse() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> requested = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bridge/api/v1/scripts", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            requested.set(exchange.getRequestURI().toString());
            respond(exchange, 200, "{\"items\":[],\"total\":0}", "application/json");
        });
        server.start();

        String credential = "fixture-credential";
        BridgeProperties properties = configured(server, credential);
        BridgeClient client = new BridgeClient(properties, new ObjectMapper(), HttpClient.newHttpClient());

        BridgeClient.BridgeResponse response = client.get("/api/v1/scripts?page=2&page_size=25");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().path("total").asInt()).isZero();
        assertThat(requested.get()).isEqualTo("/bridge/api/v1/scripts?page=2&page_size=25");
        assertThat(authorization.get()).isEqualTo("Bearer " + credential);
        assertThat(response.body().toString()).doesNotContain(credential);
    }

    @Test
    void forwardsPostBodyAndIdempotencyKey() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> idempotency = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bridge/api/v1/runs", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            idempotency.set(exchange.getRequestHeaders().getFirst("Idempotency-Key"));
            respond(exchange, 202, "{\"run_id\":\"run_01ARZ3NDEKTSV4RRFFQ69G5FAV\",\"state\":\"QUEUED\"}",
                    "application/json");
        });
        server.start();

        BridgeClient client = new BridgeClient(configured(server, "fixture-credential"),
                new ObjectMapper(), HttpClient.newHttpClient());
        BridgeClient.BridgeResponse response = client.post("/api/v1/runs",
                new ObjectMapper().readTree("{\"script_id\":\"scr_orders_0123456789abcdfg\",\"parameters\":{}}"),
                "fixture-request-key");

        assertThat(response.status()).isEqualTo(202);
        assertThat(body.get()).contains("script_id").doesNotContain("Authorization");
        assertThat(idempotency.get()).isEqualTo("fixture-request-key");
    }

    @Test
    void streamsMultipartUploadWithCredentialOnlyOnServerSide() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<byte[]> body = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bridge/api/v1/files", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            method.set(exchange.getRequestMethod());
            body.set(exchange.getRequestBody().readAllBytes());
            respond(exchange, 201,
                    "{\"file_id\":\"fil_01ARZ3NDEKTSV4RRFFQ69G5FAV\",\"status\":\"AVAILABLE\"}",
                    "application/json");
        });
        server.start();

        String credential = "fixture-upload-credential";
        BridgeClient client = new BridgeClient(configured(server, credential),
                new ObjectMapper(), HttpClient.newHttpClient());
        MockMultipartFile file = new MockMultipartFile(
                "file", "input_excel.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "xlsx-fixture-content".getBytes(StandardCharsets.UTF_8));

        BridgeClient.BridgeResponse response = client.upload("/api/v1/files", file);

        String multipart = new String(body.get(), StandardCharsets.UTF_8);
        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().path("file_id").asText())
                .isEqualTo("fil_01ARZ3NDEKTSV4RRFFQ69G5FAV");
        assertThat(method.get()).isEqualTo("POST");
        assertThat(contentType.get()).startsWith("multipart/form-data; boundary=");
        assertThat(authorization.get()).isEqualTo("Bearer " + credential);
        assertThat(multipart)
                .contains("name=\"file\"")
                .contains("filename=\"input_excel.xlsx\"")
                .contains("xlsx-fixture-content")
                .doesNotContain(credential);
        assertThat(response.body().toString()).doesNotContain(credential);
    }

    @Test
    void convertsNonJsonAndDisabledBridgeToSafeErrors() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/bridge/api/v1/queue", exchange -> respond(exchange, 500,
                "upstream html with internal details", "text/html"));
        server.start();
        BridgeClient client = new BridgeClient(configured(server, "fixture-credential"),
                new ObjectMapper(), HttpClient.newHttpClient());

        BridgeClient.BridgeResponse invalid = client.get("/api/v1/queue");
        assertThat(invalid.status()).isEqualTo(502);
        assertThat(invalid.body().path("code").asText()).isEqualTo("BRIDGE_INVALID_RESPONSE");
        assertThat(invalid.body().toString()).doesNotContain("internal details");

        BridgeProperties disabled = configured(server, "fixture-credential");
        disabled.setEnabled(false);
        BridgeClient.BridgeResponse unavailable = new BridgeClient(disabled,
                new ObjectMapper(), HttpClient.newHttpClient()).get("/api/v1/scripts");
        assertThat(unavailable.status()).isEqualTo(503);
        assertThat(unavailable.body().path("code").asText()).isEqualTo("BRIDGE_UNAVAILABLE");
    }

    private static BridgeProperties configured(HttpServer server, String credential) {
        BridgeProperties properties = new BridgeProperties();
        properties.setEnabled(true);
        properties.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/bridge");
        properties.setServiceToken(credential);
        properties.setConnectTimeout(Duration.ofSeconds(2));
        properties.setRequestTimeout(Duration.ofSeconds(2));
        return properties;
    }

    private static void respond(HttpExchange exchange, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
