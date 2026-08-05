package com.fons.cloud.ai.rag2okf.model;

import com.fons.cloud.ai.rag2okf.infrastructure.model.AesGcmCredentialCipher;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * API Key AES-GCM 凭证边界测试。
 *
 * @author hongqy
 */
class AesGcmCredentialCipherTest {

    @Test
    void shouldEncryptEachCredentialWithANewNonceAndDecryptOnlyWithTheConfiguredKey() {
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(key, "v1", "rag2okf");

        var first = cipher.encrypt("api-key-value");
        var second = cipher.encrypt("api-key-value");

        assertThat(first.ciphertext()).isNotEqualTo(second.ciphertext());
        assertThat(first.nonce()).isNotEqualTo(second.nonce());
        assertThat(cipher.decrypt(first)).isEqualTo("api-key-value");
        assertThat(first.keyVersion()).isEqualTo("v1");
    }

    @Test
    void shouldFailClosedWhenTheDeploymentKeyIsInvalid() {
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher("not-base64", "v1", "rag2okf");

        assertThatThrownBy(() -> cipher.encrypt("api-key-value"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void shouldDeriveKeyFromSaltWhenNoExplicitKeyConfigured() {
        // 未配置 model-credential-key，从 salt 派生密钥
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher("", "", "rag2okf");

        var encrypted = cipher.encrypt("api-key-value");

        // 派生密钥版本前缀为 "salt:"
        assertThat(encrypted.keyVersion()).isEqualTo("salt:rag2okf");

        // 同一 salt 派生的 cipher 能解密
        AesGcmCredentialCipher anotherCipher = new AesGcmCredentialCipher("", "", "rag2okf");
        assertThat(anotherCipher.decrypt(encrypted)).isEqualTo("api-key-value");
    }
}
