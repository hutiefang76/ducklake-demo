package com.lanxinai.data.paltform.ducklake.bridge;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class BridgeConfiguration {

    @Bean("bridgeHttpClient")
    HttpClient bridgeHttpClient(BridgeProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
