package com.dextercai.dbamcp.application.database;

import java.util.Locale;
import java.util.regex.Pattern;

/** Validation for conventional, unquoted Oracle identifiers used as bound dictionary-query values. */
public final class OracleIdentifier {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_$#]{0,127}");

    private OracleIdentifier() {
    }

    public static String normalize(String fieldName, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + " is required");
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!IDENTIFIER.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " must be an unquoted Oracle identifier");
        }
        return normalized;
    }
}
