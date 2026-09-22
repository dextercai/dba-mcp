package com.dextercai.dbamcp.config;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "dba.http.api-token=mcp-test-token",
        "dba.http.allowed-origins=*"
})
@AutoConfigureMockMvc
@ActiveProfiles("http")
class McpBoundarySecurityTest {
    private static String jdbcUrl;

    @BeforeAll static void database() throws Exception {
        Path file = Files.createTempFile("dba-mcp-boundary-", ".db");
        Files.deleteIfExists(file);
        jdbcUrl = "jdbc:sqlite:" + file;
    }

    @DynamicPropertySource static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("dba.assets.sqlite.jdbc-url", () -> jdbcUrl);
    }

    @Autowired MockMvc mvc;

    @Test
    void wildcardOriginConfigurationDoesNotRejectBrowserOrigin() throws Exception {
        mvc.perform(post("/mcp")
                        .header("Origin", "https://untrusted.example")
                        .header("Authorization", "Bearer mcp-test-token"))
                .andExpect(result -> assertNotEquals(403, result.getResponse().getStatus()));
    }

    @Test
    void wildcardOriginConfigurationStillRequiresBearerToken() throws Exception {
        mvc.perform(post("/mcp")
                        .header("Origin", "https://untrusted.example")
                        .header("Authorization", "Bearer wrong-token"))
                .andExpect(status().isUnauthorized());
    }
}
