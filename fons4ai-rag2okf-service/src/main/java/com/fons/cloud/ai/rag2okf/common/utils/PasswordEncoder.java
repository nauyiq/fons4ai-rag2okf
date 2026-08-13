package com.fons.cloud.ai.rag2okf.common.utils;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * @author hongqy
 */
public class PasswordEncoder {

    private final static org.springframework.security.crypto.password.PasswordEncoder PASSWORD_ENCODER = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    public static String hash(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        return passwordHash != null && PASSWORD_ENCODER.matches(rawPassword, passwordHash);
    }

}
