package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 基于 Spring Security DelegatingPasswordEncoder 的密码摘要适配器。
 *
 * @author hongqy
 */
@Component
public class SpringSecurityPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    @Override
    public String hash(String rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String passwordHash) {
        return passwordHash != null && passwordEncoder.matches(rawPassword, passwordHash);
    }
}
