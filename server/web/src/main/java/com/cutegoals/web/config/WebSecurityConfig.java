package com.cutegoals.web.config;

import com.cutegoals.common.constant.AuthConstants;
import com.cutegoals.common.exception.BusinessException;
import com.cutegoals.common.exception.ErrorCode;
import com.cutegoals.auth.service.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import jakarta.servlet.ServletException;

/**
 * Spring Security configuration.
 * Defines public endpoints and JWT token validation filter.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(WebSecurityConfig.class);

    private final TokenService tokenService;

    /** Public endpoints that do not require authentication. */
    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/initialize",
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/recover",
            "/api/auth/child/login",
            "/api/family/devices/children",
            "/api/health",
            "/api/instance/status"
    );



    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // CSRF handled via double-submit cookie pattern
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS.toArray(new String[0])).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(csrfFilter(), OncePerRequestFilter.class)
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** HTTP methods that require CSRF validation */
    private static final Set<String> CSRF_PROTECTED_METHODS = new HashSet<>(Arrays.asList(
            "POST", "PUT", "DELETE", "PATCH"
    ));

    @Bean
    public OncePerRequestFilter csrfFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                String path = request.getRequestURI();
                String method = request.getMethod();

                // Skip CSRF for safe methods
                if (!CSRF_PROTECTED_METHODS.contains(method)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Skip CSRF for public paths
                boolean isPublic = false;
                for (String p : PUBLIC_PATHS) {
                    if (path.equals(p) || path.startsWith(p + "/")) {
                        isPublic = true;
                        break;
                    }
                }
                if (isPublic) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // Double-submit cookie pattern: validate X-CSRF-Token header vs cookie
                String csrfCookie = null;
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie c : cookies) {
                        if (AuthConstants.COOKIE_CSRF_TOKEN.equals(c.getName())) {
                            csrfCookie = c.getValue();
                            break;
                        }
                    }
                }

                String csrfHeader = request.getHeader(AuthConstants.HEADER_CSRF);

                if (csrfCookie == null || csrfHeader == null || !csrfCookie.equals(csrfHeader)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"FORBIDDEN\",\"message\":\"CSRF token validation failed\",\"data\":null}");
                    return;
                }

                filterChain.doFilter(request, response);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setExposedHeaders(List.of(AuthConstants.HEADER_CSRF, AuthConstants.HEADER_REQUEST_ID));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * JWT authentication filter.
     * Reads access token from cookie or Authorization header.
     */
    @Bean
    public OncePerRequestFilter jwtAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws IOException {
                try {
                    String path = request.getRequestURI();

                    // Skip JWT check for public paths
                    if (isPublicPath(path, request.getMethod())) {
                        filterChain.doFilter(request, response);
                        return;
                    }

                    String accessToken = extractToken(request);

                    if (accessToken != null) {
                        var claims = tokenService.parseAccessToken(accessToken);
                        request.setAttribute(AuthConstants.ATTR_ACCOUNT_ID, claims.accountId());
                        request.setAttribute(AuthConstants.ATTR_ROLES, claims.roles());
                        request.setAttribute(AuthConstants.ATTR_SESSION_ID, claims.sessionId());
                    }

                    filterChain.doFilter(request, response);
                } catch (BusinessException e) {
                    if (e.getErrorCode() == ErrorCode.UNAUTHORIZED) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required\",\"data\":null}");
                    } else {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal error\",\"data\":null}");
                    }
                } catch (Exception e) {
                    log.error("JWT filter error", e);
                    response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Internal error\",\"data\":null}");
                }
            }

            private boolean isPublicPath(String path, String method) {
                // POST to /api/auth/initialize, /api/auth/login, /api/auth/refresh, /api/auth/recover
                // GET to /api/health, /api/instance/status
                for (String p : PUBLIC_PATHS) {
                    // Exact match or sub-path (e.g., /api/auth/recover/initiate matches /api/auth/recover)
                    if (path.equals(p) || path.startsWith(p + "/")) {
                        return true;
                    }
                }
                return false;
            }

            private String extractToken(HttpServletRequest request) {
                // Try cookie first
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (AuthConstants.COOKIE_ACCESS_TOKEN.equals(cookie.getName())) {
                            return cookie.getValue();
                        }
                    }
                }

                // Fallback to Authorization header
                String authHeader = request.getHeader("Authorization");
                if (authHeader != null && authHeader.startsWith("Bearer ")) {
                    return authHeader.substring(7);
                }

                return null;
            }
        };
    }
}
