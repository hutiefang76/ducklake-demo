package com.lanxinai.data.paltform.ducklake;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalDotEnvLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsQuotedUnquotedAndExportedValues() throws IOException {
        Path env = tempDir.resolve(".env");
        Files.writeString(env, """
                # 本地开发配置
                PG_HOST=postgres.example.internal
                PG_USER="dev"
                export S3_USE_SSL='false'
                EMPTY_VALUE=
                """);

        LocalDotEnvLoader.LoadedDotEnv loaded = LocalDotEnvLoader.load(env);

        assertThat(loaded.values())
                .containsEntry("PG_HOST", "postgres.example.internal")
                .containsEntry("PG_USER", "dev")
                .containsEntry("S3_USE_SSL", "false")
                .containsEntry("EMPTY_VALUE", "");
    }
}
