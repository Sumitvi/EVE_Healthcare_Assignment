package com.evehealthcare.diagnostics.config;

import com.evehealthcare.diagnostics.exception.ErrorResponse;
import com.evehealthcare.diagnostics.security.JwtAuthFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private static final String[] PUBLIC = {
            "/auth/signup", "/auth/signup/", "/auth/login", "/auth/login/",
            // the webhook is authenticated by an HMAC signature instead of a JWT
            "/payments/webhook", "/payments/webhook/",
            "/actuator/health", "/actuator/health/**",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/centres/**", "/tests/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/centres/**", "/tests/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/centres/**", "/tests/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/centres/**", "/tests/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) -> write(mapper, res, 401, "Unauthorized",
                                "Authentication is required or the token is invalid/expired", req.getRequestURI()))
                        .accessDeniedHandler((req, res, ex) -> write(mapper, res, 403, "Forbidden",
                                "You do not have permission to perform this action", req.getRequestURI())))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private void write(ObjectMapper mapper, HttpServletResponse res, int status, String error, String message,
                       String path) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), ErrorResponse.of(status, error, message, path));
    }
}
