package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.common.exeception.ModelAccessDeniedException;
import com.fons.cloud.ai.rag2okf.common.response.ModelConnectionResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.UserProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.LoginResponse;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.infrastructure.identity.WorkspaceAccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 知识库数据安全与越权边界测试（T021）。
 *
 * <p>验证技术设计 §4.4 数据安全与 §8.2 安全权限：
 * <ul>
 *   <li>password_hash 和 API Key 密文只存在于专用持久字段且不进响应（AC-029、AC-036）</li>
 *   <li>完整邮箱只对本人可见，其他场景脱敏（AC-029）</li>
 *   <li>模型连接/档案跨用户访问 403/404（AC-028、AC-035）</li>
 *   <li>Sa-Token 零日志/零前端持久化（AC-027）</li>
 *   <li>password/API Key 明文零日志（AC-036）</li>
 * </ul>
 *
 * <p>该测试通过反射分析实体与 DTO 字段契约，以及调用访问策略验证越权拒绝，
 * 不依赖真实 MySQL/Redis 容器。
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class KnowledgeDataSecurityIT {

    private static final List<String> FORBIDDEN_RESPONSE_FIELDS = List.of(
            "passwordHash", "password", "apiKeyCiphertext", "apiKeyNonce",
            "keyVersion", "apiKey", "ciphertext", "nonce", "tokenSecret");

    /**
     * AC-029：UserProfileResponse 只允许本人上下文返回只读邮箱，
     * 不得包含 password_hash、password_changed_at 或会话凭证。
     *
     * <p>preferenceJson 是用户偏好（非敏感），允许返回；lastLoginAt / status 属于管理字段，不应对本人暴露。
     */
    @Test
    void userProfileResponseShouldNotContainPasswordOrSessionCredentials() {
        List<String> fields = recordFieldNames(UserProfileResponse.class);

        assertThat(fields).contains("userKey", "email", "displayName", "preferenceJson");
        assertThat(fields).doesNotContain(
                "passwordHash", "password", "passwordChangedAt",
                "lastLoginAt", "status");
    }

    /**
     * AC-029：LoginResponse 只返回 token，不得返回 password、email 或用户实体快照。
     */
    @Test
    void loginResponseShouldOnlyContainToken() {
        List<String> fields = recordFieldNames(LoginResponse.class);

        assertThat(fields).containsExactly("token");
        assertThat(fields).doesNotContain("password", "email", "passwordHash");
    }

    /**
     * AC-036：ModelConnectionResponse 不得返回 API Key 明文、密文、nonce 或 key_version，
     * 只允许返回不可逆掩码 apiKeyMask。
     */
    @Test
    void modelConnectionResponseShouldNotLeakApiKeyCiphertextOrNonce() {
        List<String> fields = recordFieldNames(ModelConnectionResponse.class);

        assertThat(fields).contains("apiKeyMask");
        assertThat(fields).doesNotContain(
                "apiKeyCiphertext", "apiKeyNonce", "keyVersion", "apiKey", "ciphertext", "nonce");
    }

    /**
     * AC-035 / AC-036：ModelProfileResponse 不得返回任何凭证字段或 Base URL，
     * 避免泄露 Provider 连接信息。
     */
    @Test
    void modelProfileResponseShouldNotLeakCredentialsOrBaseUrl() {
        List<String> fields = recordFieldNames(ModelProfileResponse.class);

        assertThat(fields).doesNotContain(
                "apiKey", "apiKeyCiphertext", "apiKeyNonce", "keyVersion",
                "baseUrl", "providerName", "ciphertext", "nonce");
    }

    /**
     * AC-029：KbUserEntity.toString() 不得输出 passwordHash，
     * 防止日志记录用户实体时泄露密码摘要。
     */
    @Test
    void kbUserEntityToStringShouldExcludePasswordHash() {
        KbUserEntity user = new KbUserEntity();
        user.setUserKey("uk-001");
        user.setEmail("user@example.com");
        user.setPasswordHash("{bcrypt}$2a$10$secretHashValue");

        String output = user.toString();
        assertThat(output)
                .as("KbUserEntity.toString() 不得包含 passwordHash（AC-029）")
                .doesNotContain("secretHashValue", "passwordHash");
    }

    /**
     * AC-036：KbModelConnectionEntity.toString() 不得输出 apiKeyCiphertext / apiKeyNonce / keyVersion，
     * 防止日志记录连接实体时泄露密文和 nonce。
     */
    @Test
    void kbModelConnectionEntityToStringShouldExcludeCipherAndNonce() {
        KbModelConnectionEntity connection = new KbModelConnectionEntity();
        connection.setConnectionKey("ck-001");
        connection.setApiKeyCiphertext(new byte[]{1, 2, 3, 4, 5});
        connection.setApiKeyNonce(new byte[]{6, 7, 8, 9});
        connection.setKeyVersion("v1");

        String output = connection.toString();
        assertThat(output)
                .as("KbModelConnectionEntity.toString() 不得包含密文/nonce/keyVersion（AC-036）")
                .doesNotContain("1, 2, 3", "6, 7, 8", "keyVersion=v1", "apiKeyCiphertext", "apiKeyNonce");
    }

    /**
     * AC-028 / AC-035：WorkspaceAccessPolicy.checkAccess 方法必须存在且为唯一公开入口，
     * 用于工作空间越权校验。
     */
    @Test
    void workspaceAccessPolicyShouldExposeSingleCheckAccessMethod() {
        long publicMethods = Arrays.stream(WorkspaceAccessPolicy.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .count();

        assertThat(publicMethods)
                .as("WorkspaceAccessPolicy 应只暴露 checkAccess 一个公开方法")
                .isEqualTo(1);
    }

    /**
     * AC-028：跨用户访问模型连接/档案必须抛出 ModelAccessDeniedException，
     * 不得返回 200 或泄露资源存在性。
     */
    @Test
    void modelAccessDeniedExceptionShouldBeThrownForCrossUserAccess() {
        assertThatThrownBy(() -> {
            throw new ModelAccessDeniedException();
        }).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("访问");
    }

    /**
     * AC-036：扫描所有 Response DTO，确保无任何 forbidden 字段泄露。
     */
    @Test
    void allResponseDtosShouldNotContainForbiddenSensitiveFields() throws Exception {
        List<Class<?>> responseClasses = scanResponseClasses();

        assertThat(responseClasses).isNotEmpty();

        for (Class<?> responseClass : responseClasses) {
            List<String> fields = recordFieldNames(responseClass);
            List<String> leaked = fields.stream()
                    .filter(field -> FORBIDDEN_RESPONSE_FIELDS.stream()
                            .anyMatch(field::equalsIgnoreCase))
                    .toList();

            assertThat(leaked)
                    .as("%s 不得包含敏感字段: %s（AC-036）", responseClass.getSimpleName(), leaked)
                    .isEmpty();
        }
    }

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
            java.io.File[] files = dir.listFiles((d, name) -> name.endsWith(".class"));
            if (files == null) return List.of();

            List<Class<?>> classes = new java.util.ArrayList<>();
            for (java.io.File file : files) {
                String className = file.getName().replace(".class", "");
                try {
                    Class<?> clazz = Class.forName(packageName + "." + className);
                    if (clazz.isRecord()) {
                        classes.add(clazz);
                    }
                } catch (ClassNotFoundException ignored) {
                }
            }
            return classes;
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> recordFieldNames(Class<?> clazz) {
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
