package com.fons.cloud.ai.rag2okf.domain.entity.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProtocolType;
import com.fons.cloud.ai.rag2okf.common.constants.user.ModelTestStatus;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.model.user.EncryptedCredential;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link KbModelConnection} 的领域行为测试。
 */
class KbModelConnectionTest {

    @Test
    void shouldCreateActiveConnectionAndReplaceCredentialAsAWhole() {
        EncryptedCredential original = new EncryptedCredential(new byte[]{1}, new byte[]{2}, "v1");
        KbModelConnection connection = KbModelConnection.create(
                "connection-key", 10L, "OPENAI", "OpenAI", "默认连接",
                ModelProtocolType.OPENAI_COMPATIBLE, "https://api.example.com", original, "****1234");

        assertEquals(ModelConnectionStatus.ACTIVE, connection.getStatus());
        assertEquals(10L, connection.getOwnerUserId());
        assertArrayEquals(new byte[]{1}, connection.getApiKeyCiphertext());

        EncryptedCredential replacement = new EncryptedCredential(new byte[]{3}, new byte[]{4}, "v2");
        connection.replaceCredential(replacement, "****5678");

        assertArrayEquals(new byte[]{3}, connection.getApiKeyCiphertext());
        assertArrayEquals(new byte[]{4}, connection.getApiKeyNonce());
        assertEquals("v2", connection.getKeyVersion());
        assertEquals("****5678", connection.getApiKeyMask());
    }

    @Test
    void shouldUpdateOnlyProvidedConfigurationAndRecordTestResult() {
        KbModelConnection connection = KbModelConnection.create(
                "connection-key", 10L, "OPENAI", "OpenAI", "默认连接",
                ModelProtocolType.OPENAI_COMPATIBLE, "https://api.example.com",
                new EncryptedCredential(new byte[]{1}, new byte[]{2}, "v1"), "****1234");
        Date testedAt = new Date();

        connection.updateConfiguration(null, "新连接名", null, ModelConnectionStatus.DISABLED);
        connection.recordTestResult(ModelTestStatus.FAILED, testedAt,
                Rag2OkfResultCode.MODEL_TEST_FAILED.getCode());

        assertEquals("OpenAI", connection.getProviderName());
        assertEquals("新连接名", connection.getDisplayName());
        assertEquals("https://api.example.com", connection.getBaseUrl());
        assertEquals(ModelConnectionStatus.DISABLED, connection.getStatus());
        assertEquals(ModelTestStatus.FAILED, connection.getLastTestStatus());
        assertEquals(testedAt, connection.getLastTestAt());
        assertEquals("RF300005", connection.getLastTestErrorCode());
    }
}
