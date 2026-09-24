package com.inkomoko.mobilebanking.config;

import com.inkomoko.mobilebanking.repositories.AdminUserRepository;
import com.inkomoko.mobilebanking.security.JwtAuthFilter;
import com.inkomoko.mobilebanking.security.JwtService;
import com.inkomoko.mobilebanking.services.admin.PasswordPolicyService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;

/**
 * Security:
 *   /api/admin/**               JWT required; each endpoint checks its privilege (@PreAuthorize)
 *   /api/auth/me, change-pwd    JWT required
 *   everything else             open: USSD gateway, payment callback (own token), login,
 *                               portal static files, simulator, health, API docs
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** Client PINs and portal passwords are stored as salted bcrypt hashes. */
    @Bean
    public PasswordEncoder pinEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    /** Portal users are authenticated by AdminUserService / JWT — disables Boot's generated default user. */
    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            throw new UsernameNotFoundException(username);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, AdminUserRepository users,
                                                   PasswordPolicyService policy) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(b -> b.disable())
                .formLogin(f -> f.disable())
                .logout(l -> l.disable())
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/admin/**").authenticated()
                        .requestMatchers("/api/auth/me", "/api/auth/change-password").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) ->
                                json(res, HttpServletResponse.SC_UNAUTHORIZED, "Please sign in"))
                        .accessDeniedHandler((req, res, ex) ->
                                json(res, HttpServletResponse.SC_FORBIDDEN, "You do not have permission for this action")))
                .addFilterBefore(new JwtAuthFilter(jwt, users, policy), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void json(HttpServletResponse res, int status, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"success\":false,\"message\":\"" + message + "\"}");
    }
}
