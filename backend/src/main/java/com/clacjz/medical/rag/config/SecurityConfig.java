package com.clacjz.medical.rag.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.clacjz.medical.rag.constant.ErrorCode;
import com.clacjz.medical.rag.context.TenantContextFilter;
import com.clacjz.medical.rag.model.vo.Result;
import com.clacjz.medical.rag.security.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final TenantContextFilter tenantContextFilter;
    private final UserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Value("${app.cors.allowed-methods:GET,POST,PUT,DELETE,OPTIONS}")
    private String allowedMethods;

    @Value("${app.cors.allowed-headers:Authorization,Content-Type,X-Requested-With,X-Access-Token,X-Refresh-Token}")
    private String allowedHeaders;

    @Value("${app.cors.exposed-headers:Authorization}")
    private String exposedHeaders;

    @Value("${app.cors.allow-credentials:false}")
    private boolean allowCredentials;

    @Value("${app.cors.max-age:3600}")
    private long maxAge;

    private final Environment environment;

    private boolean isProduction() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler())
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers((RequestMatcher) request ->
request.getDispatcherType() == DispatcherType.ASYNC).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/auth/token-config", "/auth/token-config").authenticated()
                        .requestMatchers("/api/v1/auth/**", "/auth/**").permitAll()
                        .requestMatchers("/api/v1/health/**", "/health/**").permitAll()
                        .requestMatchers("/api/v1/swagger-ui/**", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                        // Actuator: 仅health和info端点公开，其余需要认证
                        .requestMatchers("/api/v1/actuator/health", "/actuator/health",
                                "/api/v1/actuator/info", "/actuator/info").permitAll()
                        .requestMatchers("/api/v1/actuator/**", "/actuator/**").authenticated()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/departments/**", "/departments/**").authenticated()
                        .requestMatchers("/api/v1/token-monitor/**", "/token-monitor/**").authenticated()
                        .requestMatchers("/api/v1/documents/**", "/documents/**").authenticated()
                        .requestMatchers("/api/v1/audit-logs/**", "/audit-logs/**").authenticated()
                        .requestMatchers("/api/v1/collections/**", "/collections/**").authenticated()
                        .requestMatchers("/api/v1/chat/**", "/chat/**").authenticated()
                        .requestMatchers("/api/v1/users/**", "/users/**").authenticated()
                        .requestMatchers("/api/v1/audit/**", "/audit/**").authenticated()
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(tenantContextFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Result<Void> result = Result.<Void>builder()
                    .code(ErrorCode.AUTHENTICATION_FAILED.getCode())
                    .message("认证失败，请重新登录")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(result));
            response.getWriter().flush();
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");

            Result<Void> result = Result.<Void>builder()
                    .code(ErrorCode.INSUFFICIENT_PERMISSIONS.getCode())
                    .message("权限不足")
                    .timestamp(java.time.LocalDateTime.now())
                    .build();

            response.getWriter().write(objectMapper.writeValueAsString(result));
            response.getWriter().flush();
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        if (allowedOrigins != null && !allowedOrigins.isEmpty()) {
            if ("*".equals(allowedOrigins.trim())) {
                if (isProduction()) {
                    log.error("SECURITY: CORS wildcard origin ('*') is explicitly denied in production! Set app.cors.allowed-origins to specific origins.");
                    throw new IllegalStateException("CORS wildcard origin is not allowed in production environment");
                } else {
                    log.warn("CORS is configured to allow all origins ('*'). This is not recommended for production!");
                    if (allowCredentials) {
                        log.error("CORS wildcard origin cannot be used with allowCredentials=true. Falling back to restrictive config.");
                        configuration.setAllowedOrigins(List.of());
                    } else {
                        configuration.setAllowedOriginPatterns(List.of("*"));
                    }
                }
            } else {
                List<String> origins = Arrays.asList(allowedOrigins.split(","));
                configuration.setAllowedOrigins(origins);
                log.info("CORS configured with allowed origins: {}", origins);
            }
        } else {
            log.warn("No CORS allowed origins configured. CORS will deny all cross-origin requests.");
            configuration.setAllowedOrigins(List.of());
        }

        if (allowedMethods != null && !allowedMethods.isEmpty()) {
            configuration.setAllowedMethods(Arrays.asList(allowedMethods.split(",")));
        }

        if (allowedHeaders != null && !allowedHeaders.isEmpty()) {
            configuration.setAllowedHeaders(Arrays.asList(allowedHeaders.split(",")));
        } else {
            configuration.setAllowedHeaders(List.of());
        }

        if (exposedHeaders != null && !exposedHeaders.isEmpty()) {
            configuration.setExposedHeaders(Arrays.asList(exposedHeaders.split(",")));
        }

        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
