package com.lanxinai.data.paltform.ducklake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.HashMap;

@SpringBootApplication
@ConfigurationPropertiesScan
public class DuckLakeDemoApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(DuckLakeDemoApplication.class);
        LocalDotEnvLoader.LoadedDotEnv localEnv = LocalDotEnvLoader.load();
        if (localEnv != null) {
            // 默认属性优先级最低，所以系统环境变量、启动参数和 K8s Secret 都可以覆盖本地 .env。
            var defaults = new HashMap<String, Object>(localEnv.values());
            defaults.put("ducklake.local-env-source", localEnv.source().toString());
            application.setDefaultProperties(defaults);
            System.out.printf("[ducklake-demo] 已加载本地 env：%s（%d 个配置项，未输出敏感值）%n",
                    localEnv.source(), localEnv.values().size());
        }
        application.run(args);
    }
}
