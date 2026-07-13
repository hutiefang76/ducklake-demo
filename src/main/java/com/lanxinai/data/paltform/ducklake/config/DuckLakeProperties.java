package com.lanxinai.data.paltform.ducklake.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ducklake")
public class DuckLakeProperties {

    private String pgHost;
    private int pgPort = 15432;
    private String pgDatabase;
    private String pgUser;
    private String pgPassword;
    private String pgClientEncoding = "UTF8";
    private String s3Endpoint;
    private String s3Region = "us-east-1";
    private String s3UrlStyle = "path";
    private boolean s3UseSsl;
    private String s3AccessKey;
    private String s3SecretKey;
    private String dataPath;
    private String attachName = "my_lake";
    private String metadataSchema = "public";
    private String schemaName = "main";
    private String duckdbPath;
    private String extensionDirectory;
    private boolean installExtensions = true;
    private int maximumPoolSize = 1;

    public void validate() {
        require(pgHost, "PG_HOST");
        require(pgDatabase, "PG_DB");
        require(pgUser, "PG_USER");
        require(pgPassword, "PG_PASSWORD");
        require(s3Endpoint, "S3_ENDPOINT");
        require(s3Region, "S3_REGION");
        require(s3AccessKey, "S3_ACCESS_KEY");
        require(s3SecretKey, "S3_SECRET_KEY");
        require(dataPath, "DUCKLAKE_DATA_PATH");
        SqlIdentifier.requireValid(attachName, "DUCKLAKE_ATTACH_NAME");
        SqlIdentifier.requireValid(metadataSchema, "DUCKLAKE_METADATA_SCHEMA");
        SqlIdentifier.requireValid(schemaName, "DUCKLAKE_DEMO_SCHEMA");
        if (pgPort < 1 || pgPort > 65535) {
            throw new IllegalStateException("PG_PORT must be between 1 and 65535");
        }
        if (maximumPoolSize != 1) {
            throw new IllegalStateException("DUCKLAKE_POOL_SIZE must remain 1 for this embedded DuckDB demo");
        }
    }

    private static void require(String value, String environmentName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + environmentName);
        }
    }

    public String getPgHost() { return pgHost; }
    public void setPgHost(String pgHost) { this.pgHost = pgHost; }
    public int getPgPort() { return pgPort; }
    public void setPgPort(int pgPort) { this.pgPort = pgPort; }
    public String getPgDatabase() { return pgDatabase; }
    public void setPgDatabase(String pgDatabase) { this.pgDatabase = pgDatabase; }
    public String getPgUser() { return pgUser; }
    public void setPgUser(String pgUser) { this.pgUser = pgUser; }
    public String getPgPassword() { return pgPassword; }
    public void setPgPassword(String pgPassword) { this.pgPassword = pgPassword; }
    public String getPgClientEncoding() { return pgClientEncoding; }
    public void setPgClientEncoding(String pgClientEncoding) { this.pgClientEncoding = pgClientEncoding; }
    public String getS3Endpoint() { return s3Endpoint; }
    public void setS3Endpoint(String s3Endpoint) { this.s3Endpoint = s3Endpoint; }
    public String getS3Region() { return s3Region; }
    public void setS3Region(String s3Region) { this.s3Region = s3Region; }
    public String getS3UrlStyle() { return s3UrlStyle; }
    public void setS3UrlStyle(String s3UrlStyle) { this.s3UrlStyle = s3UrlStyle; }
    public boolean isS3UseSsl() { return s3UseSsl; }
    public void setS3UseSsl(boolean s3UseSsl) { this.s3UseSsl = s3UseSsl; }
    public String getS3AccessKey() { return s3AccessKey; }
    public void setS3AccessKey(String s3AccessKey) { this.s3AccessKey = s3AccessKey; }
    public String getS3SecretKey() { return s3SecretKey; }
    public void setS3SecretKey(String s3SecretKey) { this.s3SecretKey = s3SecretKey; }
    public String getDataPath() { return dataPath; }
    public void setDataPath(String dataPath) { this.dataPath = dataPath; }
    public String getAttachName() { return attachName; }
    public void setAttachName(String attachName) { this.attachName = attachName; }
    public String getMetadataSchema() { return metadataSchema; }
    public void setMetadataSchema(String metadataSchema) { this.metadataSchema = metadataSchema; }
    public String getSchemaName() { return schemaName; }
    public void setSchemaName(String schemaName) { this.schemaName = schemaName; }
    public String getDuckdbPath() { return duckdbPath; }
    public void setDuckdbPath(String duckdbPath) { this.duckdbPath = duckdbPath; }
    public String getExtensionDirectory() { return extensionDirectory; }
    public void setExtensionDirectory(String extensionDirectory) { this.extensionDirectory = extensionDirectory; }
    public boolean isInstallExtensions() { return installExtensions; }
    public void setInstallExtensions(boolean installExtensions) { this.installExtensions = installExtensions; }
    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }
}
