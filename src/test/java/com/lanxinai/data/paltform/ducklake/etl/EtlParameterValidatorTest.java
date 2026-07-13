package com.lanxinai.data.paltform.ducklake.etl;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EtlParameterValidatorTest {

    private final EtlParameterValidator validator = new EtlParameterValidator();
    private final Map<String, Map<String, Object>> schema = Map.of(
            "delta_uri", Map.of("type", "uri", "required", true,
                    "allowed_schemes", java.util.List.of("s3", "file")),
            "plan_id", Map.of("type", "string", "required", true, "min_length", 1),
            "attempt", Map.of("type", "integer", "required", false, "min", 1));

    @Test
    void acceptsValidParameters() {
        validator.validate(schema, Map.of(
                "delta_uri", "s3://dp-springboot-files/input.parquet",
                "plan_id", "plan-1",
                "attempt", 1));
    }

    @Test
    void rejectsMissingUnknownWrongTypeAndDisallowedScheme() {
        assertThatThrownBy(() -> validator.validate(schema, Map.of("plan_id", "plan-1")))
                .hasMessageContaining("delta_uri");
        assertThatThrownBy(() -> validator.validate(schema, Map.of(
                "delta_uri", "s3://bucket/a", "plan_id", "p", "surprise", true)))
                .hasMessageContaining("Unknown ETL parameter");
        assertThatThrownBy(() -> validator.validate(schema, Map.of(
                "delta_uri", "s3://bucket/a", "plan_id", "p", "attempt", "1")))
                .hasMessageContaining("expected integer");
        assertThatThrownBy(() -> validator.validate(schema, Map.of(
                "delta_uri", "https://example.com/a", "plan_id", "p")))
                .hasMessageContaining("scheme is not allowed");
    }
}
