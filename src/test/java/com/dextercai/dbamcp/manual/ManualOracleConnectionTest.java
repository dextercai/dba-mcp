package com.dextercai.dbamcp.manual;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.Console;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Properties;

/**
 * Local-only Oracle JDBC connectivity diagnostic.
 *
 * <p>This is deliberately a {@code main} program, not an automated JUnit test: it prompts for
 * credentials at runtime and never writes them to source, configuration, output, or logs.</p>
 */
public final class ManualOracleConnectionTest {

    private ManualOracleConnectionTest() {
    }

    public static void main(String[] args) throws Exception {
//        Console console = System.console();
//        if (console == null) {
//            throw new IllegalStateException("Run this diagnostic from an interactive terminal so the password is not echoed.");
//        }

        String jdbcUrl = "jdbc:oracle:thin:@//10.0.4.107:1521/orclpdb"; // required(console, "JDBC URL: ");
        String username = "mcp_ro"; // required(console, "Username: ");
        char[] password = "cwz2021".toCharArray(); // console.readPassword("Password: ");

        Properties properties = new Properties();
        properties.setProperty("username", username);
        properties.setProperty("password", new String(password));

        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(username);
        hikari.setPassword(properties.getProperty("password"));
        hikari.setMaximumPoolSize(1);
        hikari.setMinimumIdle(0);
        hikari.setConnectionTimeout(30_000);
        hikari.setValidationTimeout(5_000);
        hikari.setKeepaliveTime(300_000);
        hikari.setMaxLifetime(1_800_000);
        hikari.setIdleTimeout(600_000);
        hikari.setPoolName("manual-oracle-diagnostic");

        try (HikariDataSource dataSource = new HikariDataSource(hikari);
             Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT SYS_CONTEXT('USERENV', 'CON_NAME') AS container_name,
                            SYS_CONTEXT('USERENV', 'CURRENT_USER') AS current_user
                     FROM dual
                     """);
             ResultSet resultSet = statement.executeQuery()) {
            if (resultSet.next()) {
                System.out.printf("Connected: container=%s, user=%s, database=%s%n",
                        resultSet.getString("container_name"),
                        resultSet.getString("current_user"),
                        connection.getMetaData().getDatabaseProductVersion());
            }
        } finally {
            Arrays.fill(password, '\0');
            properties.clear();
        }
    }

    private static String required(Console console, String prompt) {
        String value = console.readLine(prompt);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A value is required for: " + prompt);
        }
        return value.trim();
    }
}
