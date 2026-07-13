package com.lanxinai.data.paltform.ducklake.scheduler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private boolean enabled;
    private String baseUrl;
    private String token;
    private String username;
    private String password;
    private Path catalogPath;
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration requestTimeout = Duration.ofSeconds(60);

    public void requireAvailable() {
        if (!enabled) {
            throw new IllegalStateException("DolphinScheduler facade is disabled; set DS_ENABLED=true");
        }
        if (!hasText(baseUrl)) {
            throw new IllegalStateException("DS_BASE_URL is required");
        }
        if (!hasToken() && !(hasText(username) && hasText(password))) {
            throw new IllegalStateException("Configure DS_TOKEN or DS_USERNAME and DS_PASSWORD");
        }
        if (catalogPath == null) {
            throw new IllegalStateException("ETL_SCHEDULER_CATALOG_PATH is required");
        }
    }

    public boolean hasToken() {
        return hasText(token);
    }

    public String authMode() {
        if (hasToken()) {
            return "token";
        }
        if (hasText(username) && hasText(password)) {
            return "session";
        }
        return "unconfigured";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Path getCatalogPath() { return catalogPath; }
    public void setCatalogPath(Path catalogPath) { this.catalogPath = catalogPath; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
}
