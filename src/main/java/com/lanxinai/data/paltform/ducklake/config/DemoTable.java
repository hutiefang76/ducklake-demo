package com.lanxinai.data.paltform.ducklake.config;

public final class DemoTable {

    public static final String TABLE_NAME = "ducklake_demo_record";

    private final String catalog;
    private final String schema;

    public DemoTable(String catalog, String schema) {
        this.catalog = SqlIdentifier.requireValid(catalog, "catalog");
        this.schema = SqlIdentifier.requireValid(schema, "schema");
    }

    public String catalog() {
        return catalog;
    }

    public String schema() {
        return schema;
    }

    public String qualifiedSchema() {
        return SqlIdentifier.quote(catalog) + "." + SqlIdentifier.quote(schema);
    }

    public String qualifiedName() {
        return qualifiedSchema() + "." + SqlIdentifier.quote(TABLE_NAME);
    }
}
