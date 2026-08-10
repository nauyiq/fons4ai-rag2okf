package com.fons.cloud.ai.rag2okf.model;

import com.fons.cloud.ai.rag2okf.common.response.ModelConnectionResponse;
import com.fons.cloud.ai.rag2okf.common.request.UpdateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.controller.ModelConfigurationController;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.infrastructure.model.AesGcmCredentialCipher;
import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PatchMapping;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * API Key 六项安全合规门禁（T033）。
 *
 * <p>覆盖传输、存储、展示、日志、独立替换入口和 SSRF；测试值为不可用的固定样本，不写普通业务日志。</p>
 *
 * @author hongqy
 */
class ApiKeySecurityComplianceTest {

    private static final String SAMPLE_KEY = "integration-only-key-value";

    @Test
    @DisplayName("传输安全：模型端点只允许 HTTPS 并拒绝 HTTP")
    void modelEndpointShouldRequireHttps() {
        ModelEndpointPolicy policy = new ModelEndpointPolicy();

        policy.validate("https://8.8.8.8/v1");
        assertThatThrownBy(() -> policy.validate("http://8.8.8.8/v1"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("存储安全：初始化 SQL 使用 VARBINARY，AES-GCM 密文可按版本解密")
    void credentialShouldUseVarbinaryAndVersionedAesGcm() throws IOException {
        String schema = Files.readString(Path.of("sql", "init-schema.sql"));
        assertThat(schema)
                .contains("api_key_ciphertext VARBINARY(2048)")
                .contains("api_key_nonce VARBINARY(32)")
                .contains("key_version VARCHAR(32)");

        String base64Key = Base64.getEncoder().encodeToString(new byte[32]);
        AesGcmCredentialCipher cipher = new AesGcmCredentialCipher(base64Key, "v-security-test", "unused");
        var encrypted = cipher.encrypt(SAMPLE_KEY);
        assertThat(encrypted.ciphertext()).isNotEqualTo(SAMPLE_KEY.getBytes());
        assertThat(encrypted.keyVersion()).isEqualTo("v-security-test");
        assertThat(cipher.decrypt(encrypted)).isEqualTo(SAMPLE_KEY);
    }

    @Test
    @DisplayName("展示安全：连接响应只有不可逆 mask，不含明文、密文、nonce 或版本字段")
    void responseShouldExposeOnlyMask() {
        var fields = Arrays.stream(ModelConnectionResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fields).contains("apiKeyMask", "apiKeyConfigured");
        assertThat(fields).doesNotContain("apiKey", "apiKeyCiphertext", "apiKeyNonce", "keyVersion");
    }

    @Test
    @DisplayName("日志安全：连接实体 toString 不输出密文、nonce、版本或样本 Key")
    void entityToStringShouldNotLeakCredentialMaterial() {
        KbModelConnectionEntity entity = new KbModelConnectionEntity();
        entity.setConnectionKey("connection-security-test");
        entity.setApiKeyCiphertext(SAMPLE_KEY.getBytes());
        entity.setApiKeyNonce(new byte[]{1, 2, 3});
        entity.setKeyVersion("v-security-test");

        assertThat(entity.toString())
                .doesNotContain(SAMPLE_KEY, "apiKeyCiphertext", "apiKeyNonce", "v-security-test");
    }

    @Test
    @DisplayName("替换安全：API Key 只通过独立 /api-key 子路径更新")
    void apiKeyReplacementShouldUseDedicatedEndpoint() throws NoSuchMethodException {
        PatchMapping mapping = ModelConfigurationController.class
                .getMethod("replaceApiKey", String.class,
                        com.fons.cloud.ai.rag2okf.common.request.ReplaceApiKeyRequest.class)
                .getAnnotation(PatchMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/model-connections/{connectionKey}/api-key");

        var updateFields = Arrays.stream(UpdateModelConnectionRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(updateFields).doesNotContain("apiKey");
    }

    @Test
    @DisplayName("SSRF：loopback、私网与链路本地地址全部拒绝")
    void endpointPolicyShouldRejectInternalNetworks() {
        ModelEndpointPolicy policy = new ModelEndpointPolicy();

        assertThatThrownBy(() -> policy.validate("https://127.0.0.1/v1")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> policy.validate("https://10.0.0.1/v1")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> policy.validate("https://169.254.169.254/latest/meta-data"))
                .isInstanceOf(RuntimeException.class);
    }
}
