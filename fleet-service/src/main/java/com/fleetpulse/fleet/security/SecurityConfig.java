package com.fleetpulse.fleet.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // auth is entirely JWT + AuthService/UserRepository based, this just stops
    // Spring Boot's auto-configuration from standing up an in-memory user with a
    // random generated password we'd never use
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException("not used, authentication is JWT-based");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // no anonymous principal: a missing/invalid token should read as 401, not 403
                .anonymous(AbstractHttpConfigurer::disable)
                // explicit about both cases: no/invalid token is 401, a valid token
                // without the right role is 403. Leaving the access-denied case to
                // Spring Security's default here was actually routing it through
                // the entry point too, i.e. also 401 -- not the distinction a REST
                // API should make between "who are you" and "you can't do that"
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> response.sendError(HttpServletResponse.SC_UNAUTHORIZED))
                        .accessDeniedHandler((request, response, accessDeniedException) -> response.sendError(HttpServletResponse.SC_FORBIDDEN)))
                .authorizeHttpRequests(auth -> auth
                        // sendError() triggers an internal forward to /error, which re-runs
                        // this whole filter chain on that new path. Our JwtAuthFilter (a
                        // OncePerRequestFilter) skips itself on error dispatch by design, so
                        // without this, that second pass finds no authentication at all and
                        // silently overwrites whatever status the original handler set (e.g.
                        // a correctly-produced 403 turning into a 401) with its own rejection
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/v1/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/v1/vehicles").hasAnyRole("ADMIN", "FLEET_MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/vehicles/*/status").hasAnyRole("ADMIN", "FLEET_MANAGER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
