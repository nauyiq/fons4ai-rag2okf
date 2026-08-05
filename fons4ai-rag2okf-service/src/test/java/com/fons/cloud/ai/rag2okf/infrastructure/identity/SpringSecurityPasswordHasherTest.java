package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring Security 密码摘要适配器的行为测试。
 *
 * @author hongqy
 */
class SpringSecurityPasswordHasherTest {

    @Test
    void shouldGenerateAlgorithmPrefixedHashAndVerifyIt() {
        SpringSecurityPasswordHasher hasher = new SpringSecurityPasswordHasher();

        String hash = hasher.hash("correct-password");

        assertThat(hash).startsWith("{bcrypt}");
        assertThat(hasher.matches("correct-password", hash)).isTrue();
        assertThat(hasher.matches("wrong-password", hash)).isFalse();
    }
}
