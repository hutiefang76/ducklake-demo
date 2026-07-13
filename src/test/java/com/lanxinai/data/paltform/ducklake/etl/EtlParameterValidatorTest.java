package com.lanxinai.data.paltform.ducklake.etl;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void resolvesSuppliedThenDefaultThenRuntimeDefaultInSchemaOrder() {
        Map<String, Map<String, Object>> orderedSchema = new LinkedHashMap<>();
        orderedSchema.put("literal", Map.of(
                "type", "string", "required", true, "default", "literal-default"));
        orderedSchema.put("request_date", Map.of(
                "type", "date", "required", true, "default_from", "runtime.request_date"));
        orderedSchema.put("optional_date", Map.of(
                "type", "date", "required", false, "default_from", "runtime.business_date"));

        Map<String, Object> resolved = validator.validate(
                orderedSchema,
                Map.of("literal", "supplied"),
                Map.of("request_date", "2026-07-13"));

        assertThat(resolved.keySet()).containsExactly("literal", "request_date");
        assertThat(resolved)
                .containsEntry("literal", "supplied")
                .containsEntry("request_date", "2026-07-13")
                .doesNotContainKey("optional_date");
    }

    @Test
    void evaluatesConditionalRequirementsAfterDefaultsForEveryContractOperator() {
        assertConditionRequiresValue("equals", "full", Map.of());
        assertConditionRequiresValue("not_equals", "full", Map.of("mode", "delta"));
        assertConditionRequiresValue("in", List.of("delta", "repair"), Map.of("mode", "delta"));
        assertConditionRequiresValue("not_in", List.of("full", "repair"), Map.of("mode", "delta"));

        Map<String, Map<String, Object>> conditional = conditionalSchema("equals", "full");
        assertThat(validator.validate(conditional, Map.of("details", "ready")))
                .containsEntry("mode", "full")
                .containsEntry("details", "ready");
    }

    @Test
    void treatsNonListInOperandsAsNotMatchedLikePythonRunner() {
        assertThat(validator.validate(conditionalSchema("in", "full"), Map.of()))
                .containsEntry("mode", "full")
                .doesNotContainKey("details");
        assertThat(validator.validate(conditionalSchema("not_in", "delta"), Map.of()))
                .containsEntry("mode", "full")
                .doesNotContainKey("details");
    }

    @Test
    void rejectsMissingRuntimeDefaultAndInvalidSchemaConstructs() {
        Map<String, Map<String, Object>> runtimeRequired = Map.of(
                "request_date", Map.of(
                        "type", "date", "required", true,
                        "default_from", "runtime.request_date"));
        assertThatThrownBy(() -> validator.validate(runtimeRequired, Map.of(), Map.of()))
                .hasMessageContaining("Missing runtime default");

        assertThatThrownBy(() -> validator.validate(Map.of(
                "bad", Map.of(
                        "type", "string", "default", "x",
                        "default_from", "runtime.request_date")), Map.of()))
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> validator.validate(Map.of(
                "mode", Map.of("type", "string"),
                "bad", Map.of(
                        "type", "string",
                        "required_when", Map.of(
                                "parameter", "mode", "operator", "exists", "value", true))), Map.of()))
                .hasMessageContaining("Unsupported required_when operator");
        assertThatThrownBy(() -> validator.validate(Map.of(
                "bad", Map.of(
                        "type", "string",
                        "required_when", Map.of(
                                "parameter", "missing", "operator", "equals", "value", true))), Map.of()))
                .hasMessageContaining("unknown parameter");
    }

    @Test
    void rejectsExplicitNullInsteadOfApplyingDefault() {
        Map<String, Object> supplied = new LinkedHashMap<>();
        supplied.put("mode", null);
        assertThatThrownBy(() -> validator.validate(Map.of(
                "mode", Map.of("type", "string", "default", "full")), supplied))
                .hasMessageContaining("must not be null");
    }

    private void assertConditionRequiresValue(
            String operator,
            Object expected,
            Map<String, Object> supplied) {
        assertThatThrownBy(() -> validator.validate(
                conditionalSchema(operator, expected), supplied))
                .hasMessageContaining("Missing conditionally required ETL parameter: details");
    }

    private static Map<String, Map<String, Object>> conditionalSchema(
            String operator,
            Object expected) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        result.put("mode", Map.of("type", "string", "required", false, "default", "full"));
        result.put("details", Map.of(
                "type", "string",
                "required", false,
                "required_when", Map.of(
                        "parameter", "mode",
                        "operator", operator,
                        "value", expected)));
        return result;
    }
}
