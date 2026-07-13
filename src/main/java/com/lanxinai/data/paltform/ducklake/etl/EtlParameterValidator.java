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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class EtlParameterValidator {

    private static final Set<Class<?>> INTEGER_TYPES = Set.of(
            Byte.class, Short.class, Integer.class, Long.class, BigInteger.class);
    private static final Set<String> RUNTIME_DEFAULTS = Set.of(
            "runtime.business_date", "runtime.request_date", "runtime.request_timestamp");
    private static final Set<String> REQUIRED_WHEN_OPERATORS = Set.of(
            "equals", "not_equals", "in", "not_in");

    public Map<String, Object> validate(
            Map<String, Map<String, Object>> schema,
            Map<String, Object> parameters) {
        return validate(schema, parameters, Map.of());
    }

    /**
     * Resolves and validates parameters using the same order as the Python ETL runner:
     * supplied value, literal default, runtime default, unconditional required, then required_when.
     */
    public Map<String, Object> validate(
            Map<String, Map<String, Object>> schema,
            Map<String, Object> parameters,
            Map<String, Object> runtime) {
        if (schema == null) throw new IllegalStateException("Workflow parameterSchema is missing");
        Map<String, Object> supplied = parameters == null ? Map.of() : parameters;
        Map<String, Object> runtimeValues = runtime == null ? Map.of() : runtime;
        for (String name : supplied.keySet()) {
            if (!schema.containsKey(name)) throw new IllegalArgumentException("Unknown ETL parameter: " + name);
        }
        Map<String, Object> resolved = new LinkedHashMap<>();
        for (var entry : schema.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> spec = entry.getValue();
            if (spec == null) throw new IllegalStateException("Invalid parameterSchema entry: " + name);
            validateSpec(name, spec, schema);

            Object value;
            if (supplied.containsKey(name)) {
                value = supplied.get(name);
            } else if (spec.containsKey("default")) {
                value = spec.get("default");
            } else if (spec.containsKey("default_from")) {
                String source = String.valueOf(spec.get("default_from"));
                String runtimeName = source.substring("runtime.".length());
                if (!runtimeValues.containsKey(runtimeName)) {
                    if (Boolean.TRUE.equals(spec.get("required"))) {
                        throw new IllegalArgumentException(
                                "Missing runtime default for ETL parameter: " + name);
                    }
                    continue;
                }
                value = runtimeValues.get(runtimeName);
            } else if (Boolean.TRUE.equals(spec.get("required"))) {
                throw new IllegalArgumentException("Missing required ETL parameter: " + name);
            } else {
                continue;
            }
            if (value == null) throw new IllegalArgumentException("ETL parameter must not be null: " + name);
            validateValue(name, value, spec);
            resolved.put(name, value);
        }

        validateConditionalRequirements(schema, supplied, resolved);
        return Collections.unmodifiableMap(new LinkedHashMap<>(resolved));
    }

    private void validateSpec(
            String name,
            Map<String, Object> spec,
            Map<String, Map<String, Object>> schema) {
        if (spec.containsKey("default") && spec.containsKey("default_from")) {
            throw new IllegalStateException(
                    "ETL parameter default and default_from are mutually exclusive: " + name);
        }
        if (spec.containsKey("default_from")) {
            String source = String.valueOf(spec.get("default_from"));
            if (!RUNTIME_DEFAULTS.contains(source)) {
                throw new IllegalStateException("Unsupported ETL parameter default_from: " + source);
            }
        }
        Object requiredWhen = spec.get("required_when");
        if (requiredWhen == null) return;
        if (!(requiredWhen instanceof Map<?, ?> condition)) {
            throw new IllegalStateException("Invalid required_when for ETL parameter: " + name);
        }
        Object parameter = condition.get("parameter");
        Object operator = condition.get("operator");
        if (!(parameter instanceof String parameterName) || !schema.containsKey(parameterName)) {
            throw new IllegalStateException("required_when references an unknown parameter: " + name);
        }
        if (!(operator instanceof String operation) || !REQUIRED_WHEN_OPERATORS.contains(operation)) {
            throw new IllegalStateException("Unsupported required_when operator for " + name + ": " + operator);
        }
        if (!condition.containsKey("value")) {
            throw new IllegalStateException("required_when value is required for ETL parameter: " + name);
        }
    }

    private void validateConditionalRequirements(
            Map<String, Map<String, Object>> schema,
            Map<String, Object> supplied,
            Map<String, Object> resolved) {
        for (var entry : schema.entrySet()) {
            Object requiredWhen = entry.getValue().get("required_when");
            if (!(requiredWhen instanceof Map<?, ?> condition)) continue;
            String parameter = (String) condition.get("parameter");
            Object left = resolved.containsKey(parameter) ? resolved.get(parameter) : supplied.get(parameter);
            Object expected = condition.get("value");
            boolean matched = switch ((String) condition.get("operator")) {
                case "equals" -> java.util.Objects.equals(left, expected);
                case "not_equals" -> !java.util.Objects.equals(left, expected);
                case "in" -> expected instanceof List<?> values && values.contains(left);
                case "not_in" -> expected instanceof List<?> values && !values.contains(left);
                default -> false; // validateSpec rejects unsupported operators before this pass.
            };
            if (matched && !resolved.containsKey(entry.getKey())) {
                throw new IllegalArgumentException(
                        "Missing conditionally required ETL parameter: " + entry.getKey());
            }
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
