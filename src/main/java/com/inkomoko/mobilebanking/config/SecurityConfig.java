package com.inkomoko.mobilebanking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfig {

    /** Client PINs are stored as salted bcrypt hashes — never plaintext or reversible. */
    @Bean
    public PasswordEncoder pinEncoder() {
        return new BCryptPasswordEncoder(10);
    }
}
