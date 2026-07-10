package com.lanxinai.data.paltform.ducklake;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

final class LocalDotEnvLoader {

    private static final Pattern KEY_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.-]*");

    private LocalDotEnvLoader() {
    }

    static LoadedDotEnv load() {
        String explicitPath = firstNonBlank(
                System.getProperty("ducklake.env.file"),
                System.getenv("DUCKLAKE_ENV_FILE")
        );
        if (explicitPath != null) {
            Path path = Path.of(explicitPath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException("Configured DuckLake env file does not exist: " + path);
            }
            return load(path);
        }

        // 同时兼容“以 ducklake-demo 为工作目录”和“以上级仓库为工作目录”的 IDE 启动方式。
        for (Path candidate : List.of(Path.of(".env"), Path.of("ducklake-demo", ".env"))) {
            Path path = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(path)) {
                return load(path);
            }
        }
        return null;
    }

    static LoadedDotEnv load(Path path) {
        Map<String, Object> values = new LinkedHashMap<>();
        try {
            int lineNumber = 0;
            for (String rawLine : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = rawLine.strip();
                if (lineNumber == 1 && line.startsWith("\uFEFF")) {
                    line = line.substring(1).strip();
                }
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).strip();
                }
                int separator = line.indexOf('=');
                if (separator < 1) {
                    throw invalidLine(path, lineNumber);
                }
                String key = line.substring(0, separator).strip();
                if (!KEY_PATTERN.matcher(key).matches()) {
                    throw invalidLine(path, lineNumber);
                }
                values.put(key, stripMatchingQuotes(line.substring(separator + 1).strip()));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read DuckLake env file: " + path, exception);
        }
        return new LoadedDotEnv(path.toAbsolutePath().normalize(), Map.copyOf(values));
    }

    private static String stripMatchingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static IllegalArgumentException invalidLine(Path path, int lineNumber) {
        return new IllegalArgumentException("Invalid dotenv entry at " + path + ":" + lineNumber);
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second != null && !second.isBlank() ? second : null;
    }

    record LoadedDotEnv(Path source, Map<String, Object> values) {
    }
}
