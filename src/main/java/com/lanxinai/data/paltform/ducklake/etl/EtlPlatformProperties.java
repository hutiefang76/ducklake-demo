package com.lanxinai.data.paltform.ducklake.etl;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "etl-platform")
public class EtlPlatformProperties {

    private boolean enabled;
    private final Artifact artifact = new Artifact();
    private final Ledger ledger = new Ledger();
    private final Reconciliation reconciliation = new Reconciliation();
    private final MaterialMaster materialMaster = new MaterialMaster();

    public void requireAvailable() {
        if (!enabled) throw new IllegalStateException("ETL platform is disabled; set ETL_PLATFORM_ENABLED=true");
        artifact.requireAvailable();
        ledger.requireAvailable();
        reconciliation.requireValid();
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Artifact getArtifact() { return artifact; }
    public Ledger getLedger() { return ledger; }
    public Reconciliation getReconciliation() { return reconciliation; }
    public MaterialMaster getMaterialMaster() { return materialMaster; }

    public static final class Artifact {
        private String endpoint;
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private String bucket = "dp-springboot-files";
        private String prefix = "data-platform-dev/etl-platform";
        private long maxBytes = 536_870_912L;

        void requireAvailable() {
            for (String value : new String[]{endpoint, region, accessKey, secretKey, bucket, prefix}) {
                if (value == null || value.isBlank()) throw new IllegalStateException("ETL artifact S3 configuration is incomplete");
            }
            if (maxBytes < 1) throw new IllegalStateException("ETL_ARTIFACT_MAX_BYTES must be positive");
        }
        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getPrefix() { return prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public long getMaxBytes() { return maxBytes; }
        public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    }

    public static final class Ledger {
        private String jdbcUrl;
        private String username;
        private String password;
        private String schema = "etl_control";

        void requireAvailable() {
            for (String value : new String[]{jdbcUrl, username, password, schema}) {
                if (value == null || value.isBlank()) throw new IllegalStateException("ETL ledger PostgreSQL configuration is incomplete");
            }
            if (!schema.matches("[a-z][a-z0-9_]{0,62}")) throw new IllegalStateException("Invalid ETL ledger schema");
        }
        public String getJdbcUrl() { return jdbcUrl; }
        public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
    }

    public static final class Reconciliation {
        private boolean enabled = true;
        private Duration fixedDelay = Duration.ofSeconds(30);
        private Duration readyStopStaleAfter = Duration.ofMinutes(2);
        private int batchSize = 100;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public Duration getFixedDelay() { return fixedDelay; }
        public void setFixedDelay(Duration fixedDelay) { this.fixedDelay = fixedDelay; }
        public Duration getReadyStopStaleAfter() { return readyStopStaleAfter; }
        public void setReadyStopStaleAfter(Duration readyStopStaleAfter) {
            this.readyStopStaleAfter = readyStopStaleAfter;
        }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        void requireValid() {
            if (fixedDelay == null || fixedDelay.isNegative() || fixedDelay.isZero()) {
                throw new IllegalStateException("ETL reconciliation fixed delay must be positive");
            }
            if (readyStopStaleAfter == null || readyStopStaleAfter.isNegative()
                    || readyStopStaleAfter.isZero()) {
                throw new IllegalStateException("ETL READY_STOP stale threshold must be positive");
            }
            if (batchSize < 1 || batchSize > 1000) {
                throw new IllegalStateException("ETL reconciliation batch size must be between 1 and 1000");
            }
        }
    }

    public static final class MaterialMaster {
        private String projectId = "notebooks-etl";
        private String workflowId = "material.master_refresh";

        public String getProjectId() { return projectId; }
        public void setProjectId(String projectId) { this.projectId = projectId; }
        public String getWorkflowId() { return workflowId; }
        public void setWorkflowId(String workflowId) { this.workflowId = workflowId; }
    }
}
