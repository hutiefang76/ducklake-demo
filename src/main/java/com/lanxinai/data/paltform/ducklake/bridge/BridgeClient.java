package com.lanxinai.data.paltform.ducklake.bridge;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;

@Component
public class BridgeClient {

    private final BridgeProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public BridgeClient(
            BridgeProperties properties,
            ObjectMapper mapper,
            @Qualifier("bridgeHttpClient") HttpClient httpClient) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = httpClient;
    }

    public BridgeResponse get(String pathAndQuery) {
        return exchange("GET", pathAndQuery, null, null);
    }

    public BridgeResponse post(String pathAndQuery, JsonNode body, String idempotencyKey) {
        return exchange("POST", pathAndQuery, body == null ? mapper.createObjectNode() : body, idempotencyKey);
    }

    private BridgeResponse exchange(String method, String pathAndQuery, JsonNode body, String idempotencyKey) {
        if (!properties.isConfigured()) {
            return error(HttpStatus.SERVICE_UNAVAILABLE.value(), "BRIDGE_UNAVAILABLE",
                    "Bridge BFF is disabled or incomplete");
        }
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(properties.resolve(pathAndQuery))
                    .timeout(properties.getRequestTimeout())
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + properties.getServiceToken());
            if (idempotencyKey != null && !idempotencyKey.isBlank()) {
                request.header("Idempotency-Key", idempotencyKey);
            }
            if ("POST".equals(method)) {
                request.header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            } else {
                request.GET();
            }
            HttpResponse<String> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
            return parse(response.statusCode(), response.body());
        } catch (HttpTimeoutException exception) {
            return error(HttpStatus.GATEWAY_TIMEOUT.value(), "BRIDGE_TIMEOUT", "Bridge request timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return error(HttpStatus.BAD_GATEWAY.value(), "BRIDGE_INTERRUPTED", "Bridge request was interrupted");
        } catch (IOException | IllegalArgumentException | IllegalStateException exception) {
            return error(HttpStatus.BAD_GATEWAY.value(), "BRIDGE_UNREACHABLE", "Bridge is unavailable");
        }
    }

    private BridgeResponse parse(int status, String content) {
        if (content == null || content.isBlank()) {
            return new BridgeResponse(status, mapper.createObjectNode());
        }
        try {
            return new BridgeResponse(status, mapper.readTree(content));
        } catch (RuntimeException exception) {
            return error(HttpStatus.BAD_GATEWAY.value(), "BRIDGE_INVALID_RESPONSE",
                    "Bridge returned a non-JSON response");
        }
    }

    private BridgeResponse error(int status, String code, String message) {
        ObjectNode body = mapper.createObjectNode();
        body.put("code", code);
        body.put("message", message);
        return new BridgeResponse(status, body);
    }

    public record BridgeResponse(int status, JsonNode body) {}
}
