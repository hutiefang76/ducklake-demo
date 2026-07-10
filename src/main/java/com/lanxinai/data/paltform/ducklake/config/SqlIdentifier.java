package com.lanxinai.data.paltform.ducklake.config;

import java.util.regex.Pattern;

public final class SqlIdentifier {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private SqlIdentifier() {
    }

    public static String requireValid(String value, String name) {
        if (value == null || !SAFE_IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match " + SAFE_IDENTIFIER.pattern());
        }
        return value;
    }

    public static String quote(String value) {
        requireValid(value, "SQL identifier");
        return '"' + value + '"';
    }
}
