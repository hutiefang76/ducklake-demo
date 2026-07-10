package com.lanxinai.data.paltform.ducklake.config;

import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

final class InitializingDuckLakeDataSource implements DataSource {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(InitializingDuckLakeDataSource.class);

    private final DuckLakeProperties properties;
    private final String jdbcUrl;

    InitializingDuckLakeDataSource(DuckLakeProperties properties, String jdbcUrl) {
        this.properties = properties;
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(jdbcUrl);
        try {
            initialize(connection);
            return connection;
        } catch (SQLException | RuntimeException failure) {
            connection.close();
            throw new SQLException("DuckLake connection initialization failed; verify PostgreSQL, S3, and extension settings", failure);
        }
    }

    private void initialize(Connection connection) throws SQLException {
        long started = System.nanoTime();
        log.info("开始初始化 DuckLake 连接：extensions=httpfs,postgres,ducklake，attach={}",
                properties.getAttachName());
        try (Statement statement = connection.createStatement()) {
            if (properties.getExtensionDirectory() != null && !properties.getExtensionDirectory().isBlank()) {
                statement.execute("SET extension_directory=" + sqlLiteral(properties.getExtensionDirectory()));
            }
            loadExtension(statement, "httpfs");
            loadExtension(statement, "postgres");
            loadExtension(statement, "ducklake");

            statement.execute("SET s3_region=" + sqlLiteral(properties.getS3Region()));
            statement.execute("SET s3_endpoint=" + sqlLiteral(s3EndpointHost(properties.getS3Endpoint())));
            statement.execute("SET s3_url_style=" + sqlLiteral(properties.getS3UrlStyle()));
            statement.execute("SET s3_use_ssl=" + properties.isS3UseSsl());
            statement.execute("SET s3_access_key_id=" + sqlLiteral(properties.getS3AccessKey()));
            statement.execute("SET s3_secret_access_key=" + sqlLiteral(properties.getS3SecretKey()));

            // PostgreSQL 密码只进入 ATTACH 语句，日志中只输出非敏感连接摘要。
            String metadata = "ducklake:postgres:dbname=" + libpqValue(properties.getPgDatabase())
                    + " host=" + libpqValue(properties.getPgHost())
                    + " port=" + properties.getPgPort()
                    + " user=" + libpqValue(properties.getPgUser())
                    + " password=" + libpqValue(properties.getPgPassword())
                    + " client_encoding=" + libpqValue(properties.getPgClientEncoding());
            String attachSql = "ATTACH " + sqlLiteral(metadata)
                    + " AS " + SqlIdentifier.quote(properties.getAttachName())
                    + " (DATA_PATH " + sqlLiteral(properties.getDataPath()) + ")";
            statement.execute(attachSql);
            statement.execute("USE " + SqlIdentifier.quote(properties.getAttachName()));
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("DuckLake ATTACH 完成：catalog={}，dataPath={}，耗时 {} ms",
                    properties.getAttachName(), properties.getDataPath(), elapsedMs);
        }
    }

    private void loadExtension(Statement statement, String extension) throws SQLException {
        try {
            statement.execute("LOAD " + extension);
            log.debug("DuckDB extension 已加载：{}", extension);
        } catch (SQLException loadFailure) {
            if (!properties.isInstallExtensions()) {
                throw loadFailure;
            }
            log.warn("DuckDB extension {} 未预装，开始在线安装", extension);
            statement.execute("INSTALL " + extension);
            statement.execute("LOAD " + extension);
            log.info("DuckDB extension 安装并加载完成：{}", extension);
        }
    }

    private static String s3EndpointHost(String endpoint) {
        String trimmed = endpoint.trim();
        if (!trimmed.contains("://")) {
            return trimmed.replaceAll("/+$", "");
        }
        URI uri = URI.create(trimmed);
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Invalid S3_ENDPOINT");
        }
        return uri.getPort() > 0 ? uri.getHost() + ":" + uri.getPort() : uri.getHost();
    }

    private static String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private static String libpqValue(String value) {
        String escaped = value.replace("\\", "\\\\").replace("'", "\\'");
        return "'" + escaped + "'";
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        throw new SQLFeatureNotSupportedException("Credentials are supplied through DuckLake environment settings");
    }

    @Override
    public PrintWriter getLogWriter() { return DriverManager.getLogWriter(); }

    @Override
    public void setLogWriter(PrintWriter out) { DriverManager.setLogWriter(out); }

    @Override
    public void setLoginTimeout(int seconds) { DriverManager.setLoginTimeout(seconds); }

    @Override
    public int getLoginTimeout() { return DriverManager.getLoginTimeout(); }

    @Override
    public Logger getParentLogger() { return Logger.getLogger("ducklake-demo"); }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Not a wrapper for " + iface.getName());
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) { return iface.isInstance(this); }
}
