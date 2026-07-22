package com.lanxinai.data.paltform.ducklake.bridge;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BridgePropertiesTest {

    @Test
    void resolvesOnlyFreshV1RoutesBelowConfiguredServiceRoot() {
        BridgeProperties properties = configured("http://bridge.internal:8080/bridge/");

        assertThat(properties.resolve("/api/v1/scripts?page=2").toString())
                .isEqualTo("http://bridge.internal:8080/bridge/api/v1/scripts?page=2");
        assertThatThrownBy(() -> properties.resolve("/internal/v1/runs/one"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsafeBaseUrlAndIncompleteConfiguration() {
        BridgeProperties properties = configured("http://user:password@bridge.internal:8080");
        assertThatThrownBy(() -> properties.resolve("/api/v1/scripts"))
                .isInstanceOf(IllegalStateException.class);

        properties.setEnabled(false);
        assertThat(properties.isConfigured()).isFalse();
    }

    private static BridgeProperties configured(String baseUrl) {
        BridgeProperties properties = new BridgeProperties();
        properties.setEnabled(true);
        properties.setBaseUrl(baseUrl);
        properties.setServiceToken("fixture-credential");
        return properties;
    }
}
