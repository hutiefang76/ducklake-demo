package com.lanxinai.data.paltform.ducklake.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;

@Configuration
public class SchedulerConfig {

    @Bean
    HttpClient dolphinSchedulerHttpClient(SchedulerProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
    }
}
