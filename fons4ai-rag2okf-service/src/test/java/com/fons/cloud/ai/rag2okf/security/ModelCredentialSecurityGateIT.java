package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.common.dto.CredentialCipher;
import com.fons.cloud.ai.rag2okf.common.dto.EncryptedCredential;
import com.fons.cloud.ai.rag2okf.application.model.ModelConfigurationApplicationService;
import com.fons.cloud.ai.rag2okf.common.dto.UserModelResolver;
import com.fons.cloud.ai.rag2okf.domain.entity.KbKnowledgeBaseEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.infrastructure.model.AesGcmCredentialCipher;
import com.fons.cloud.ai.rag2okf.infrastructure.model.ModelEndpointPolicy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 模型凭证安全门禁集成测试（T036）。
 *
 * <p>静态扫描 + 反射验证以下安全门禁，不依赖 Spring 容器：
 * <ul>
 *   <li>零全局模型 Key：application.yml 不含 langchain4j 全局模型配置</li>
 *   <li>零明文凭证响应：所有 Response DTO 不含 apiKey/密文/nonce/keyVersion 字段</li>
 *   <li>零明文凭证日志：KbModelConnectionEntity.toString() 排除密文/nonce/keyVersion</li>
 *   <li>SSRF 防护：ModelEndpointPolicy.validate 拒绝 loopback/private/link-local 地址</li>
 *   <li>重定向禁用：NoRedirectHttpClient 使用 Redirect.NEVER</li>
 *   <li>跨用户隔离：UserModelResolver.resolveOwnedActiveProfile 接受 ownerUserId 参数</li>
 *   <li>fail-closed：ModelConfigurationApplicationService 不含全局 fallback 模型或 apiKey 字段</li>
 *   <li>凭证加密：AesGcmCredentialCipher 实现 CredentialCipher，encrypt 返回 EncryptedCredential</li>
 *   <li>固定字段残留：KbKnowledgeBaseEntity 不含 chatModelProfileId/embeddingModelProfileId</li>
 * </ul>
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class ModelCredentialSecurityGateIT {

    private static final Path APPLICATION_YML = Paths.get("src/main/resources/application.yml");
    private static final Path FACTORY_SOURCE =
            Paths.get("src/main/java/com/fons/cloud/ai/rag2okf/infrastructure/model/LangChain4jModelClientFactory.java");
    private static final Path RESOLVER_SOURCE =
            Paths.get("src/main/java/com/fons/cloud/ai/rag2okf/application/model/UserModelResolver.java");

    private static final List<String> FORBIDDEN_CREDENTIAL_FIELDS = List.of(
            "apiKey", "apiKeyCiphertext", "apiKeyNonce", "keyVersion", "ciphertext", "nonce");

    @Nested
    @DisplayName("零全局模型 Key")
    class ZeroGlobalModelKey {

        @Test
        @DisplayName("application.yml 不含 langchain4j.open-ai / langchain4j.openai 配置")
        void applicationYmlShouldNotContainGlobalLangChain4jModelKey() throws IOException {
            String yml = Files.readString(APPLICATION_YML);

            assertThat(yml)
                    .as("application.yml 不得包含 langchain4j.open-ai 配置")
                    .doesNotContain("langchain4j.open-ai");
            assertThat(yml)
                    .as("application.yml 不得包含 langchain4j.openai 配置")
                    .doesNotContain("langchain4j.openai");
        }
    }

    @Nested
    @DisplayName("零明文凭证响应")
    class ZeroPlaintextCredentialResponse {

        @Test
        @DisplayName("所有 Response DTO 不含明文凭证字段")
        void allResponseDtosShouldNotContainPlaintextCredentialFields() throws Exception {
            List<Class<?>> responseClasses = scanResponseClasses();
            assertThat(responseClasses).isNotEmpty();

            for (Class<?> responseClass : responseClasses) {
                List<String> fields = fieldNames(responseClass);
                List<String> leaked = fields.stream()
                        .filter(field -> FORBIDDEN_CREDENTIAL_FIELDS.stream()
                                .anyMatch(field::equalsIgnoreCase))
                        .toList();

                assertThat(leaked)
                        .as("%s 不得包含明文凭证字段: %s", responseClass.getSimpleName(), leaked)
                        .isEmpty();
            }
        }
    }

    @Nested
    @DisplayName("零明文凭证日志")
    class ZeroPlaintextCredentialLog {

        @Test
        @DisplayName("KbModelConnectionEntity toString 不输出密文/nonce/keyVersion")
        void kbModelConnectionEntityToStringShouldExcludeCipherAndNonce() {
            KbModelConnectionEntity connection = new KbModelConnectionEntity();
            connection.setConnectionKey("ck-001");
            connection.setApiKeyCiphertext(new byte[]{1, 2, 3, 4, 5});
            connection.setApiKeyNonce(new byte[]{6, 7, 8, 9});
            connection.setKeyVersion("v1-secret-version");

            String output = connection.toString();
            assertThat(output)
                    .as("toString 不得输出 apiKeyCiphertext")
                    .doesNotContain("apiKeyCiphertext");
            assertThat(output)
                    .as("toString 不得输出 apiKeyNonce")
                    .doesNotContain("apiKeyNonce");
            assertThat(output)
                    .as("toString 不得输出 keyVersion 值")
                    .doesNotContain("keyVersion=v1-secret-version");
        }

        @Test
        @DisplayName("toString 输出不含密文/nonce/keyVersion 值")
        void toStringOutputShouldNotLeakCipherOrNonce() {
            KbModelConnectionEntity connection = new KbModelConnectionEntity();
            connection.setConnectionKey("ck-001");
            connection.setApiKeyCiphertext(new byte[]{1, 2, 3, 4, 5});
            connection.setApiKeyNonce(new byte[]{6, 7, 8, 9});
            connection.setKeyVersion("v1");

            String output = connection.toString();
            assertThat(output)
                    .as("KbModelConnectionEntity.toString() 不得输出密文/nonce/keyVersion")
                    .doesNotContain("1, 2, 3", "6, 7, 8", "keyVersion=v1",
                            "apiKeyCiphertext", "apiKeyNonce");
        }
    }

    @Nested
    @DisplayName("SSRF 防护")
    class SsrfProtection {

        @Test
        @DisplayName("ModelEndpointPolicy 类存在且暴露 validate 方法")
        void modelEndpointPolicyShouldExposeValidateMethod() {
            assertThat(Arrays.stream(ModelEndpointPolicy.class.getDeclaredMethods())
                    .anyMatch(m -> "validate".equals(m.getName())))
                    .as("ModelEndpointPolicy 必须暴露 validate 方法")
                    .isTrue();
        }

        @Test
        @DisplayName("validate 拒绝 loopback 地址")
        void validateShouldRejectLoopbackAddress() {
            ModelEndpointPolicy policy = new ModelEndpointPolicy();

            assertThatThrownBy(() -> policy.validate("https://127.0.0.1/v1"))
                    .as("loopback 地址必须被拒绝")
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("validate 拒绝 private 地址")
        void validateShouldRejectPrivateAddress() {
            ModelEndpointPolicy policy = new ModelEndpointPolicy();

            assertThatThrownBy(() -> policy.validate("https://10.0.0.1/v1"))
                    .as("private 地址必须被拒绝")
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("validate 拒绝 link-local 地址")
        void validateShouldRejectLinkLocalAddress() {
            ModelEndpointPolicy policy = new ModelEndpointPolicy();

            assertThatThrownBy(() -> policy.validate("https://169.254.169.254/latest/meta-data"))
                    .as("link-local 地址必须被拒绝")
                    .isInstanceOf(RuntimeException.class);
        }
    }

    @Nested
    @DisplayName("重定向禁用")
    class RedirectDisabled {

        @Test
        @DisplayName("NoRedirectHttpClient 使用 Redirect.NEVER")
        void noRedirectHttpClientShouldUseNeverRedirect() throws IOException {
            String source = Files.readString(FACTORY_SOURCE);

            assertThat(source)
                    .as("NoRedirectHttpClient 必须使用 Redirect.NEVER 禁用重定向")
                    .contains("Redirect.NEVER");
        }
    }

    @Nested
    @DisplayName("跨用户隔离")
    class CrossUserIsolation {

        @Test
        @DisplayName("resolveOwnedActiveProfile 签名包含 ownerUserId 参数")
        void resolveOwnedActiveProfileShouldAcceptOwnerUserId() throws Exception {
            Method method = UserModelResolver.class.getDeclaredMethod(
                    "resolveOwnedActiveProfile", String.class, Long.class);

            assertThat(method.getParameterCount())
                    .as("resolveOwnedActiveProfile 必须接受 2 个参数")
                    .isEqualTo(2);
            assertThat(method.getParameterTypes()[1])
                    .as("第二个参数类型必须为 Long（ownerUserId）")
                    .isEqualTo(Long.class);

            String source = Files.readString(RESOLVER_SOURCE);
            assertThat(source)
                    .as("方法签名必须包含 ownerUserId 参数名")
                    .contains("resolveOwnedActiveProfile(String profileKey, Long ownerUserId)");
        }
    }

    @Nested
    @DisplayName("fail-closed 安全策略")
    class FailClosedPolicy {

        @Test
        @DisplayName("ModelConfigurationApplicationService 不含全局 fallback 模型或 apiKey 字段")
        void modelConfigurationServiceShouldNotContainGlobalFallbackOrApiKey() {
            List<String> fieldNames = Arrays.stream(ModelConfigurationApplicationService.class.getDeclaredFields())
                    .map(Field::getName)
                    .toList();

            assertThat(fieldNames)
                    .as("不得有字段名包含 apiKey")
                    .noneMatch(name -> name.toLowerCase().contains("apikey"));
            assertThat(fieldNames)
                    .as("不得有字段名包含 fallback")
                    .noneMatch(name -> name.toLowerCase().contains("fallback"));
            assertThat(fieldNames)
                    .as("不得有字段名包含 defaultModel 或 globalModel")
                    .noneMatch(name -> name.toLowerCase().contains("defaultmodel")
                            || name.toLowerCase().contains("globalmodel"));
        }
    }

    @Nested
    @DisplayName("凭证加密")
    class CredentialEncryption {

        @Test
        @DisplayName("AesGcmCredentialCipher 实现 CredentialCipher 接口")
        void aesGcmCredentialCipherShouldImplementCredentialCipher() {
            assertThat(CredentialCipher.class.isAssignableFrom(AesGcmCredentialCipher.class))
                    .as("AesGcmCredentialCipher 必须实现 CredentialCipher 接口")
                    .isTrue();
        }

        @Test
        @DisplayName("encrypt 方法返回 EncryptedCredential")
        void encryptMethodShouldReturnEncryptedCredential() throws Exception {
            Method encryptMethod = AesGcmCredentialCipher.class.getDeclaredMethod("encrypt", String.class);

            assertThat(encryptMethod.getReturnType())
                    .as("encrypt 方法必须返回 EncryptedCredential")
                    .isEqualTo(EncryptedCredential.class);
        }

        @Test
        @DisplayName("EncryptedCredential 包含 ciphertext、nonce、keyVersion 三个组件")
        void encryptedCredentialShouldContainCipherNonceAndKeyVersion() {
            List<String> components = Arrays.stream(EncryptedCredential.class.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();

            assertThat(components)
                    .as("EncryptedCredential 必须包含 ciphertext、nonce、keyVersion")
                    .contains("ciphertext", "nonce", "keyVersion");
        }
    }

    @Nested
    @DisplayName("固定字段残留扫描")
    class FixedFieldResidueScan {

        @Test
        @DisplayName("KbKnowledgeBaseEntity 不含 chatModelProfileId/embeddingModelProfileId 固定字段")
        void kbKnowledgeBaseEntityShouldNotContainFixedModelProfileFields() {
            List<String> fieldNames = Arrays.stream(KbKnowledgeBaseEntity.class.getDeclaredFields())
                    .map(Field::getName)
                    .toList();

            assertThat(fieldNames)
                    .as("KbKnowledgeBaseEntity 不得包含 chatModelProfileId 固定字段")
                    .doesNotContain("chatModelProfileId");
            assertThat(fieldNames)
                    .as("KbKnowledgeBaseEntity 不得包含 embeddingModelProfileId 固定字段")
                    .doesNotContain("embeddingModelProfileId");
        }
    }

    // ==================== Helper Methods ====================

    private List<Class<?>> scanResponseClasses() throws Exception {
        String packageName = "com.fons.cloud.ai.rag2okf.common.response";
        String path = packageName.replace('.', '/');

        List<Class<?>> result = new java.util.ArrayList<>();
        Collections.list(getClass().getClassLoader().getResources(path))
                .forEach(url -> result.addAll(scanPackage(url, packageName)));
        return result.stream().distinct().toList();
    }

    @SuppressWarnings("unchecked")
    private List<Class<?>> scanPackage(java.net.URL url, String packageName) {
        try {
            java.io.File dir = new java.io.File(url.toURI());
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".class") && !name.contains("$"));
            if (files == null) return List.of();

            List<Class<?>> classes = new java.util.ArrayList<>();
            for (java.io.File file : files) {
                String className = file.getName().replace(".class", "");
                try {
                    classes.add(Class.forName(packageName + "." + className));
                } catch (ClassNotFoundException ignored) {
                }
            }
            return classes;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> fieldNames(Class<?> clazz) {
        if (clazz.isRecord()) {
            return Arrays.stream(clazz.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList();
        }
        return Arrays.stream(clazz.getDeclaredFields())
                .map(Field::getName)
                .toList();
    }
}
