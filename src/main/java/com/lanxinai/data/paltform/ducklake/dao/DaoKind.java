package com.lanxinai.data.paltform.ducklake.dao;

import java.util.Arrays;

public enum DaoKind {
    JDBC("jdbc"),
    JDBC_TEMPLATE("jdbc-template"),
    MYBATIS("mybatis"),
    JPA("jpa");

    private final String pathValue;

    DaoKind(String pathValue) {
        this.pathValue = pathValue;
    }

    public String pathValue() {
        return pathValue;
    }

    public static DaoKind fromPath(String value) {
        return Arrays.stream(values())
                .filter(kind -> kind.pathValue.equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported DAO '" + value + "'; use jdbc, jdbc-template, mybatis, or jpa"));
    }
}
