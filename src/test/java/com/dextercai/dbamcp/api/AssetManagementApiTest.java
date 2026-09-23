package com.dextercai.dbamcp.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Files;
import java.nio.file.Path;
import com.dextercai.dbamcp.domain.asset.AssetId;
import com.dextercai.dbamcp.domain.asset.AssetRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("http")
class AssetManagementApiTest {
    private static String jdbcUrl;
    @BeforeAll static void database() throws Exception { Path file = Files.createTempFile("dba-mcp-assets-", ".db"); Files.deleteIfExists(file); jdbcUrl = "jdbc:sqlite:" + file; }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("dba.assets.sqlite.jdbc-url", () -> jdbcUrl);
        registry.add("dba.http.asset-admin-username", () -> "asset-admin");
        registry.add("dba.http.asset-admin-password-hash", () -> new BCryptPasswordEncoder().encode("test-password"));
    }
    @Autowired MockMvc mvc;
    @Autowired AssetRepository assets;

    @Test void requiresBasicAuthenticationAndNeverReturnsWrittenPassword() throws Exception {
        String body = """
                {"type":"DATABASE_INSTANCE","code":"oracle-api-test","displayName":"Oracle API Test","detail":{"database_type":"ORACLE","host":"127.0.0.1","port":1521,"connection_properties":{"username":"readonly","password":"not-for-output"}}}
                """;
        mvc.perform(post("/api/v1/assets").contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        String result = mvc.perform(post("/api/v1/assets").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("asset-admin", "test-password")).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.detail.connection_properties.password").doesNotExist()).andReturn().getResponse().getContentAsString();
        com.fasterxml.jackson.databind.JsonNode created = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
        String id = created.at("/asset/id/value").asText();
        mvc.perform(get("/api/v1/assets/{id}", id).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("asset-admin", "test-password")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("not-for-output"))));
        String update = """
                {"type":"DATABASE_INSTANCE","code":"oracle-api-test","displayName":"Oracle API Test","detail":{"database_type":"ORACLE","host":"127.0.0.1","port":1521,"connection_properties":{"username":"readonly"}}}
                """;
        mvc.perform(patch("/api/v1/assets/{id}", id).param("version", created.at("/asset/version").asText())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic("asset-admin", "test-password"))
                        .contentType("application/json").content(update))
                .andExpect(status().isOk()).andExpect(jsonPath("$.detail.connection_properties.password").doesNotExist());
        assertEquals("not-for-output", assets.findDatabaseDetail(new AssetId(id)).orElseThrow().connectionProperties().get("password"));
    }
    @Test void publishesOpenApiDescription() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.paths['/api/v1/assets'].post.requestBody.content['application/json'].examples['Oracle asset with JDBC credentials'].value.detail.connection_properties.username").value("dba_mcp_ro"))
                .andExpect(jsonPath("$.paths['/api/v1/assets'].post.requestBody.content['application/json'].examples['Oracle asset with JDBC credentials'].value.detail.connection_properties.password").value("<database-password>"))
                .andExpect(jsonPath("$.paths['/api/v1/assets'].post.requestBody.content['application/json'].examples['Dedicated Oracle user-unlock asset'].value.detail.read_only").value(false))
                .andExpect(jsonPath("$.paths['/api/v1/assets'].post.requestBody.content['application/json'].examples['Dedicated Oracle user-unlock asset'].value.detail.user_unlock_enabled").value(true));
    }
}
