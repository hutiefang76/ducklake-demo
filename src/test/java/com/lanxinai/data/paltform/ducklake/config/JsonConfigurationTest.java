package com.lanxinai.data.paltform.ducklake.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonConfigurationTest {

    @Test
    void registersJacksonTwoMapperForSchedulerAndEtlServices() throws Exception {
        try (var context = new AnnotationConfigApplicationContext(JsonConfiguration.class)) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            assertEquals("{\"ok\":true}", mapper.writeValueAsString(new Payload(true)));
        }
    }

    private record Payload(boolean ok) {
    }
}
