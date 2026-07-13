package com.lanxinai.data.paltform.ducklake.etl;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class EtlParameterValidator {

    private static final Set<Class<?>> INTEGER_TYPES = Set.of(
            Byte.class, Short.class, Integer.class, Long.class, BigInteger.class);

    public void validate(Map<String, Map<String, Object>> schema, Map<String, Object> parameters) {
        if (schema == null) throw new IllegalStateException("Workflow parameterSchema is missing");
        Map<String, Object> supplied = parameters == null ? Map.of() : parameters;
        for (String name : supplied.keySet()) {
            if (!schema.containsKey(name)) throw new IllegalArgumentException("Unknown ETL parameter: " + name);
        }
        for (var entry : schema.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> spec = entry.getValue();
            if (spec == null) throw new IllegalStateException("Invalid parameterSchema entry: " + name);
            boolean required = Boolean.TRUE.equals(spec.get("required"))
                    && !spec.containsKey("default") && !spec.containsKey("default_from");
            if (!supplied.containsKey(name)) {
                if (required) throw new IllegalArgumentException("Missing required ETL parameter: " + name);
                continue;
            }
            Object value = supplied.get(name);
            if (value == null) throw new IllegalArgumentException("ETL parameter must not be null: " + name);
            validateValue(name, value, spec);
        }
    }

    private void validateValue(String name, Object value, Map<String, Object> spec) {
        String type = String.valueOf(spec.get("type"));
        boolean valid = switch (type) {
            case "string", "enum", "uri", "asset_ref", "date", "datetime" -> value instanceof String;
            case "integer" -> INTEGER_TYPES.contains(value.getClass());
            case "number" -> value instanceof Number && !(value instanceof Boolean);
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof List<?>;
            case "object" -> value instanceof Map<?, ?>;
            case "artifact" -> validArtifact(value);
            default -> throw new IllegalStateException("Unsupported ETL parameter type: " + type);
        };
        if (!valid) throw new IllegalArgumentException("Invalid ETL parameter type for " + name + ": expected " + type);
        if (type.equals("enum") && spec.get("values") instanceof List<?> values && !values.contains(value)) {
            throw new IllegalArgumentException("ETL parameter is not an allowed enum value: " + name);
        }
        if (value instanceof String text) validateString(name, text, type, spec);
        if (value instanceof Number number) validateNumber(name, number, spec);
    }

    private void validateString(String name, String value, String type, Map<String, Object> spec) {
        if (spec.get("min_length") instanceof Number min && value.length() < min.intValue()) {
            throw new IllegalArgumentException("ETL parameter is shorter than allowed: " + name);
        }
        if (spec.get("max_length") instanceof Number max && value.length() > max.intValue()) {
            throw new IllegalArgumentException("ETL parameter is longer than allowed: " + name);
        }
        if (spec.get("pattern") instanceof String pattern && !Pattern.matches(pattern, value)) {
            throw new IllegalArgumentException("ETL parameter does not match pattern: " + name);
        }
        try {
            if (type.equals("date")) LocalDate.parse(value);
            if (type.equals("datetime")) parseDateTime(value);
            if (type.equals("uri")) {
                URI uri = URI.create(value);
                if (uri.getScheme() == null) throw new IllegalArgumentException();
                if (spec.get("allowed_schemes") instanceof List<?> schemes && !schemes.contains(uri.getScheme())) {
                    throw new IllegalArgumentException("ETL parameter URI scheme is not allowed: " + name);
                }
            }
        } catch (DateTimeParseException | IllegalArgumentException exception) {
            if (exception.getMessage() != null && exception.getMessage().startsWith("ETL parameter")) throw exception;
            throw new IllegalArgumentException("Invalid ETL parameter value: " + name, exception);
        }
    }

    private void validateNumber(String name, Number value, Map<String, Object> spec) {
        BigDecimal number = new BigDecimal(value.toString());
        if (spec.get("min") instanceof Number min && number.compareTo(new BigDecimal(min.toString())) < 0) {
            throw new IllegalArgumentException("ETL parameter is below minimum: " + name);
        }
        if (spec.get("max") instanceof Number max && number.compareTo(new BigDecimal(max.toString())) > 0) {
            throw new IllegalArgumentException("ETL parameter is above maximum: " + name);
        }
    }

    private static void parseDateTime(String value) {
        try { OffsetDateTime.parse(value); return; } catch (DateTimeParseException ignored) {}
        try { Instant.parse(value); return; } catch (DateTimeParseException ignored) {}
        LocalDateTime.parse(value);
    }

    private static boolean validArtifact(Object value) {
        if (!(value instanceof Map<?, ?> artifact)) return false;
        return artifact.get("artifact_id") instanceof String
                && artifact.get("uri") instanceof String
                && artifact.get("sha256") instanceof String
                && artifact.get("size") instanceof Number
                && artifact.get("content_type") instanceof String;
    }
}
