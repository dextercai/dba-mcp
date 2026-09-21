package com.dextercai.dbamcp.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.filter.OncePerRequestFilter;

/** A deployment must replace the bearer-token bridge with its OIDC/mTLS integration before public exposure. */
@Configuration
@Profile("http")
@EnableWebSecurity
class HttpMcpSecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService assetAdminUserDetails(DbaProperties properties) {
        return username -> {
            DbaProperties.Http http = properties.http();
            if (http.assetAdminUsername() == null || http.assetAdminUsername().isBlank() || http.assetAdminPasswordHash() == null || http.assetAdminPasswordHash().isBlank() || !http.assetAdminUsername().equals(username)) {
                throw new UsernameNotFoundException("asset administration is not configured");
            }
            return User.withUsername(http.assetAdminUsername()).password(http.assetAdminPasswordHash()).roles("ASSET_ADMIN").build();
        };
    }

    @Bean
    SecurityFilterChain mcpSecurityFilterChain(HttpSecurity http, DbaProperties properties) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/actuator/health/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/**").hasRole("ASSET_ADMIN")
                        .anyRequest().permitAll())
                .httpBasic(basic -> { })
                .addFilterBefore(new McpBoundaryFilter(properties.http()), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static final class McpBoundaryFilter extends OncePerRequestFilter {
        private final String token; private final Set<String> origins;
        McpBoundaryFilter(DbaProperties.Http config) {
            token = config.apiToken();
            origins = config.allowedOrigins() == null ? Set.of() : Arrays.stream(config.allowedOrigins().split(",")).map(String::trim).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        }
        @Override protected boolean shouldNotFilter(HttpServletRequest request) { return !request.getRequestURI().equals("/mcp"); }
        @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
            String origin = request.getHeader("Origin");
            if (origin != null && !origins.contains(origin)) { response.sendError(HttpServletResponse.SC_FORBIDDEN, "origin is not allowed"); return; }
            if (token == null || token.isBlank()) { response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "MCP authentication is not configured"); return; }
            String supplied = request.getHeader("Authorization");
            if (!constantTimeEquals("Bearer " + token, supplied)) { response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "authentication required"); return; }
            chain.doFilter(request, response);
        }
        private static boolean constantTimeEquals(String expected, String actual) {
            if (actual == null) return false;
            return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
