package com.lanxinai.data.paltform.ducklake.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DemoTableTest {

    @Test
    void buildsAQuotedQualifiedName() {
        DemoTable table = new DemoTable("my_lake", "main");
        assertThat(table.qualifiedName()).isEqualTo("\"my_lake\".\"main\".\"ducklake_demo_record\"");
    }

    @Test
    void rejectsUnsafeIdentifiers() {
        assertThatThrownBy(() -> new DemoTable("my_lake;drop", "main"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
