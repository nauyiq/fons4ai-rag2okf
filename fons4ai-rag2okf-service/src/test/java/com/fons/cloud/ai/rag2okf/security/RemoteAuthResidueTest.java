package com.fons.cloud.ai.rag2okf.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 远程认证残留静态扫描测试（T028）。
 *
 * <p>固化以下清理结论，防止回归：
 * <ul>
 *   <li>pom.xml 不含 Auth Service API、Communication API、Dubbo 认证 adapter、邮件发送依赖</li>
 *   <li>源码不含 registration-code/email-verification/refresh endpoint</li>
 *   <li>源码不含 clientSecret/phoneNumber/smsCode/verifyCode 字段</li>
 *   <li>源码不含 @CrossOrigin/Cookie token 读取/CSRF token 残留</li>
 *   <li>源码不含 Dubbo 注解</li>
 *   <li>源码不含邮件发送类</li>
 *   <li>application.yml 不含邮件服务配置</li>
 * </ul>
 *
 * @author hongqy
 */
@Execution(ExecutionMode.CONCURRENT)
class RemoteAuthResidueTest {

    private static final Path PROJECT_ROOT = Paths.get("src/main");
    private static final Path POM_XML = Paths.get("pom.xml");
    private static final Path APPLICATION_YML = Paths.get("src/main/resources/application.yml");

    private static final List<String> FORBIDDEN_POM_ARTIFACTS = List.of(
            "fons4cloud-auth-api", "fons4cloud-auth-service",
            "fons4cloud-communication", "fons4cloud-sms",
            "spring-boot-starter-mail", "javax.mail", "jakarta.mail",
            "fons4cloud-auth-dubbo"
    );

    private static final List<String> FORBIDDEN_SOURCE_SYMBOLS = List.of(
            "registration-code", "email-verification", "email_verified", "verified_at",
            "refresh-token", "/auth/refresh",
            "clientSecret", "client_secret", "clientId", "client_id",
            "phoneNumber", "phone_number", "smsCode", "sms_code",
            "verifyCode", "verify_code",
            "@DubboReference", "@DubboService", "@DubboMethod",
            "org.apache.dubbo",
            "JavaMailSender", "MailSender", "SimpleMailMessage", "MimeMessage",
            "javax.mail", "jakarta.mail"
    );

    private static final List<String> FORBIDDEN_SOURCE_PATTERNS = List.of(
            "@CrossOrigin"
    );

    private static final List<String> FORBIDDEN_YML_KEYS = List.of(
            "spring.mail", "spring.smtp", "mail.sender", "email.sender"
    );

    /**
     * pom.xml 不得包含远程认证、通信、Dubbo 认证或邮件发送依赖。
     */
    @Test
    void pomXmlShouldNotContainForbiddenAuthDependencies() throws IOException {
        String pomContent = Files.readString(POM_XML);

        for (String artifact : FORBIDDEN_POM_ARTIFACTS) {
            assertThat(pomContent)
                    .as("pom.xml 不得包含禁用依赖: %s", artifact)
                    .doesNotContain(artifact);
        }
    }

    /**
     * pom.xml 只允许引入 fons4cloud-auth-satoken，不得引入其他 auth 模块。
     */
    @Test
    void pomXmlShouldOnlyUseSaTokenAuthModule() throws IOException {
        String pomContent = Files.readString(POM_XML);

        assertThat(pomContent)
                .as("pom.xml 应包含 fons4cloud-auth-satoken（合法本地会话依赖）")
                .contains("fons4cloud-auth-satoken");

        long authArtifactCount = countOccurrences(pomContent, "fons4cloud-auth-");
        assertThat(authArtifactCount)
                .as("pom.xml 中 fons4cloud-auth-* 依赖应只有 1 个（satoken）")
                .isEqualTo(1);
    }

    /**
     * 源码不得包含远程认证端点、敏感字段或 Dubbo 注解。
     */
    @Test
    void sourceShouldNotContainRemoteAuthResidue() throws IOException {
        List<String> sourceFiles = collectSourceFiles(PROJECT_ROOT);

        for (String fileContent : sourceFiles) {
            for (String symbol : FORBIDDEN_SOURCE_SYMBOLS) {
                assertThat(fileContent)
                        .as("源码不得包含禁用符号: %s", symbol)
                        .doesNotContain(symbol);
            }
            for (String pattern : FORBIDDEN_SOURCE_PATTERNS) {
                assertThat(fileContent)
                        .as("源码不得包含禁用模式: %s", pattern)
                        .doesNotContain(pattern);
            }
        }
    }

    /**
     * application.yml 不得包含邮件服务配置。
     */
    @Test
    void applicationYmlShouldNotContainMailConfiguration() throws IOException {
        String ymlContent = Files.readString(APPLICATION_YML);

        for (String key : FORBIDDEN_YML_KEYS) {
            assertThat(ymlContent)
                    .as("application.yml 不得包含邮件服务配置: %s", key)
                    .doesNotContain(key);
        }
    }

    /**
     * application.yml 必须配置纯 Header Bearer 模式（is-read-cookie=false, is-read-header=true）。
     */
    @Test
    void applicationYmlShouldEnforceHeaderOnlyBearerMode() throws IOException {
        String ymlContent = Files.readString(APPLICATION_YML);

        assertThat(ymlContent)
                .as("Sa-Token 必须禁用 Cookie 读取（is-read-cookie: false）")
                .contains("is-read-cookie: false");
        assertThat(ymlContent)
                .as("Sa-Token 必须启用 Header 读取（is-read-header: true）")
                .contains("is-read-header: true");
        assertThat(ymlContent)
                .as("Sa-Token 不得向响应头写入 token（is-write-header: false）")
                .contains("is-write-header: false");
        assertThat(ymlContent)
                .as("Sa-Token 不得输出日志（is-log: false）")
                .contains("is-log: false");
    }

    /**
     * application.yml 的 Sa-Token 白名单只应包含 login、registration 和 error。
     */
    @Test
    void applicationYmlShouldOnlyExcludeLoginRegistrationAndError() throws IOException {
        String ymlContent = Files.readString(APPLICATION_YML);

        assertThat(ymlContent).contains("/auth/login");
        assertThat(ymlContent).contains("/auth/registration");
        assertThat(ymlContent).contains("/error");
        assertThat(ymlContent)
                .as("Sa-Token 白名单不得包含 refresh 端点")
                .doesNotContain("/auth/refresh");
        assertThat(ymlContent)
                .as("Sa-Token 白名单不得包含 email-verification 端点")
                .doesNotContain("/auth/email-verification");
        assertThat(ymlContent)
                .as("Sa-Token 白名单不得包含 registration-code 端点")
                .doesNotContain("/auth/registration-code");
    }

    private List<String> collectSourceFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".yml") || p.toString().endsWith(".yaml"))
                    .map(p -> {
                        try {
                            return Files.readString(p);
                        } catch (IOException e) {
                            return "";
                        }
                    })
                    .toList();
        }
    }

    private long countOccurrences(String text, String substring) {
        long count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
    }
}
