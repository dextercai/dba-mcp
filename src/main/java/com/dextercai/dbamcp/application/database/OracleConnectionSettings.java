package com.dextercai.dbamcp.application.database;

import com.dextercai.dbamcp.domain.database.DatabaseDetail;
import java.util.Properties;

/** Normalizes one asset's Oracle endpoint, credentials, and non-sensitive driver properties. */
record OracleConnectionSettings(String jdbcUrl, String username, String password, Properties driverProperties) {
    static OracleConnectionSettings from(DatabaseDetail detail) {
        String jdbcUrl = detail.connectionProperties().get("jdbcUrl");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            jdbcUrl = "jdbc:oracle:thin:@//" + detail.host() + ":" + detail.port() + "/" + detail.serviceName();
        }
        if (!jdbcUrl.startsWith("jdbc:oracle:thin:@") || containsCredentialParameter(jdbcUrl)) {
            throw new IllegalArgumentException("jdbcUrl must be an Oracle Thin endpoint without embedded credentials");
        }

        String username = detail.connectionProperties().get("username");
        String password = detail.connectionProperties().get("password");
        if (detail.connectionProperties().containsKey("user")) {
            throw new IllegalArgumentException("use username instead of the driver property user");
        }
        if (password != null && (username == null || username.isBlank())) {
            throw new IllegalArgumentException("username is required when password is configured");
        }

        Properties driverProperties = new Properties();
        detail.connectionProperties().forEach((key, value) -> {
            if (!key.equals("jdbcUrl") && !key.equals("username") && !key.equals("password")) {
                driverProperties.setProperty(key, value);
            }
        });
        return new OracleConnectionSettings(jdbcUrl, username, password, driverProperties);
    }

    private static boolean containsCredentialParameter(String jdbcUrl) {
        String normalized = jdbcUrl.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("?user=") || normalized.contains("&user=")
                || normalized.contains("?username=") || normalized.contains("&username=")
                || normalized.contains("?password=") || normalized.contains("&password=");
    }
}
