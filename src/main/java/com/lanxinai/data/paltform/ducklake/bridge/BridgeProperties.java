package com.lanxinai.data.paltform.ducklake.bridge;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

@ConfigurationProperties(prefix = "bridge")
public class BridgeProperties {

    private boolean enabled;
    private String baseUrl;
    private String serviceToken;
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration requestTimeout = Duration.ofSeconds(30);

    public boolean isConfigured() {
        return enabled && hasText(baseUrl) && hasText(serviceToken);
    }

    URI resolve(String pathAndQuery) {
        if (!isConfigured()) {
            throw new IllegalStateException("Bridge BFF is disabled or incomplete");
        }
        URI base = URI.create(baseUrl.trim());
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null) {
            throw new IllegalStateException("BRIDGE_BASE_URL must be an HTTP(S) service root without credentials, query or fragment");
        }
        if (!pathAndQuery.startsWith("/api/v1/")) {
            throw new IllegalArgumentException("Only fresh Bridge v1 business routes are allowed");
        }
        String root = base.toString().replaceAll("/+$", "");
        return URI.create(root + pathAndQuery);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getServiceToken() { return serviceToken; }
    public void setServiceToken(String serviceToken) { this.serviceToken = serviceToken; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
}
