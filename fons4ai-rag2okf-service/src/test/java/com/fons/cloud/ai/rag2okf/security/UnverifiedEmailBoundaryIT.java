package com.fons.cloud.ai.rag2okf.security;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 未验证邮箱边界集成测试（T029）。
 *
 * <p>固化以下安全边界，防止回归：
 * <ul>
 *   <li>零邮箱验证残留：KbUserEntity 无 emailVerified/verifiedAt 字段</li>
 *   <li>零密码找回入口：源码无密码重置/找回相关 endpoint 和方法</li>
 *   <li>零邮件发送调用：源码无 JavaMailSender/MailSender 引用</li>
 *   <li>注册冲突异常不携带差异化信息</li>
 *   <li>认证失败异常不携带差异化信息</li>
 *   <li>登录响应 DTO 不含邮箱字段</li>
 * </ul>
 *
 * @author hongqy
 */
class UnverifiedEmailBoundaryIT {

    private static final Path MAIN_SOURCE_ROOT = Paths.get("src/main");
    private static final Path TEST_SOURCE_ROOT = Paths.get("src/test");

    private static final List<String> FORBIDDEN_EMAIL_VERIFICATION_SYMBOLS = List.of(
            "emailVerified", "email_verified", "verifiedAt", "verified_at",
            "isVerified", "is_verified", "emailConfirm", "email_confirm",
            "verificationCode", "verification_code", "confirmEmail", "confirm_email"
    );

    private static final List<String> FORBIDDEN_PASSWORD_RECOVERY_SYMBOLS = List.of(
            "passwordReset", "password_reset", "resetPassword", "reset_password",
            "forgotPassword", "forgot_password", "findPassword", "find_password",
            "passwordRecover", "password_recover", "recoverPassword", "recover_password",
            "sendResetEmail", "send_reset_email", "/auth/forgot", "/auth/reset",
            "/auth/recover", "/auth/password-reset"
    );

    private static final List<String> FORBIDDEN_MAIL_SYMBOLS = List.of(
            "JavaMailSender", "MailSender", "SimpleMailMessage", "MimeMessage",
            "javax.mail", "jakarta.mail", "MailSendException"
    );

    @Nested
    @DisplayName("零邮箱验证残留")
    class ZeroEmailVerification {

        @Test
        @DisplayName("KbUserEntity 不含邮箱验证相关字段")
        void kbUserEntityShouldNotHaveEmailVerificationFields() {
            Field[] fields = KbUserEntity.class.getDeclaredFields();

            List<String> fieldNames = Arrays.stream(fields)
                    .map(Field::getName)
                    .toList();

            for (String forbidden : FORBIDDEN_EMAIL_VERIFICATION_SYMBOLS) {
                assertThat(fieldNames)
                        .as("KbUserEntity 不得包含邮箱验证字段: %s", forbidden)
                        .doesNotContain(forbidden);
            }
        }

        @Test
        @DisplayName("源码不含邮箱验证相关符号")
        void sourceShouldNotContainEmailVerificationSymbols() throws IOException {
            List<String> javaFiles = collectJavaFiles(MAIN_SOURCE_ROOT);

            for (String fileContent : javaFiles) {
                for (String symbol : FORBIDDEN_EMAIL_VERIFICATION_SYMBOLS) {
                    assertThat(fileContent)
                            .as("源码不得包含邮箱验证符号: %s", symbol)
                            .doesNotContain(symbol);
                }
            }
        }

        @Test
        @DisplayName("application.yml 不含邮箱验证配置")
        void applicationYmlShouldNotContainEmailVerificationConfig() throws IOException {
            String ymlContent = Files.readString(Paths.get("src/main/resources/application.yml"));

            assertThat(ymlContent)
                    .as("配置不得包含 email-verification 端点")
                    .doesNotContain("email-verification");
            assertThat(ymlContent)
                    .as("配置不得包含 registration-code 端点")
                    .doesNotContain("registration-code");
            assertThat(ymlContent)
                    .as("Sa-Token 白名单不得包含 email-verification")
                    .doesNotContain("/auth/email-verification");
        }
    }

    @Nested
    @DisplayName("零密码找回入口")
    class ZeroPasswordRecovery {

        @Test
        @DisplayName("源码不含密码找回相关 endpoint 和方法")
        void sourceShouldNotContainPasswordRecoverySymbols() throws IOException {
            List<String> javaFiles = collectJavaFiles(MAIN_SOURCE_ROOT);

            for (String fileContent : javaFiles) {
                for (String symbol : FORBIDDEN_PASSWORD_RECOVERY_SYMBOLS) {
                    assertThat(fileContent)
                            .as("源码不得包含密码找回符号: %s", symbol)
                            .doesNotContain(symbol);
                }
            }
        }

        @Test
        @DisplayName("application.yml 不含密码找回端点")
        void applicationYmlShouldNotContainPasswordRecoveryPaths() throws IOException {
            String ymlContent = Files.readString(Paths.get("src/main/resources/application.yml"));

            assertThat(ymlContent)
                    .as("Sa-Token 白名单不得包含密码找回端点")
                    .doesNotContain("/auth/forgot")
                    .doesNotContain("/auth/reset")
                    .doesNotContain("/auth/recover")
                    .doesNotContain("/auth/password-reset");
        }
    }

    @Nested
    @DisplayName("零邮件发送调用")
    class ZeroMailSending {

        @Test
        @DisplayName("源码不含邮件发送类引用")
        void sourceShouldNotContainMailSendingSymbols() throws IOException {
            List<String> javaFiles = collectJavaFiles(MAIN_SOURCE_ROOT);

            for (String fileContent : javaFiles) {
                for (String symbol : FORBIDDEN_MAIL_SYMBOLS) {
                    assertThat(fileContent)
                            .as("源码不得包含邮件发送符号: %s", symbol)
                            .doesNotContain(symbol);
                }
            }
        }
    }

    @Nested
    @DisplayName("注册冲突防枚举")
    class RegistrationConflictAntiEnumeration {

        @Test
        @DisplayName("EmailAlreadyRegisteredException 使用 PARAMS_ERROR 而非 CONFLICT")
        void shouldUseParamsErrorNotConflict() {
            // EmailAlreadyRegisteredException 构造函数使用 ResultCode.PARAMS_ERROR
            // 而非 ResultCode.CONFLICT，避免通过状态码差异枚举已注册邮箱
            com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException ex =
                    new com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException();

            assertThat(ex.getCode())
                    .as("注册冲突应返回 PARAMS_ERROR（400），而非 CONFLICT（409），避免枚举")
                    .isEqualTo(com.fons.cloud.common.result.ResultCode.PARAMS_ERROR.getCode());
        }

        @Test
        @DisplayName("EmailAlreadyRegisteredException 构造函数无参，所有实例消息一致")
        void shouldHaveConsistentMessageAcrossInstances() {
            com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException ex1 =
                    new com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException();
            com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException ex2 =
                    new com.fons.cloud.ai.rag2okf.common.exeception.EmailAlreadyRegisteredException();

            // 无参构造函数确保所有注册冲突场景产生相同的错误信息
            assertThat(ex1.getMessage()).isEqualTo(ex2.getMessage());
            assertThat(ex1.getCode()).isEqualTo(ex2.getCode());
        }
    }

    @Nested
    @DisplayName("认证失败不泄露原因")
    class AuthenticationFailureNonLeakage {

        @Test
        @DisplayName("AuthenticationDeniedException 使用 INVALID_ACCESS_TOKEN 而非差异化错误码")
        void shouldUseInvalidAccessToken() {
            com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException ex =
                    new com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException();

            assertThat(ex.getCode())
                    .as("认证失败应返回 INVALID_ACCESS_TOKEN，不区分账号不存在/密码错误/账号禁用")
                    .isEqualTo(com.fons.cloud.common.result.ResultCode.INVALID_ACCESS_TOKEN.getCode());
        }

        @Test
        @DisplayName("AuthenticationDeniedException 构造函数无参，所有实例消息一致")
        void shouldHaveConsistentMessageAcrossInstances() {
            com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException ex1 =
                    new com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException();
            com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException ex2 =
                    new com.fons.cloud.ai.rag2okf.common.exeception.AuthenticationDeniedException();

            // 无参构造函数确保所有认证失败场景产生相同的错误信息
            assertThat(ex1.getMessage()).isEqualTo(ex2.getMessage());
            assertThat(ex1.getCode()).isEqualTo(ex2.getCode());
        }
    }

    @Nested
    @DisplayName("登录响应不含邮箱字段")
    class LoginResponseZeroEmailExposure {

        @Test
        @DisplayName("LoginResponse 只含 token 字段，不含邮箱")
        void loginResponseShouldNotContainEmail() {
            Field[] fields = com.fons.cloud.ai.rag2okf.common.response.LoginResponse.class.getDeclaredFields();

            List<String> fieldNames = Arrays.stream(fields)
                    .map(Field::getName)
                    .toList();

            assertThat(fieldNames)
                    .as("LoginResponse 只应包含 token 字段")
                    .containsExactly("token");

            for (String fieldName : fieldNames) {
                assertThat(fieldName.toLowerCase())
                        .as("LoginResponse 不得包含邮箱相关字段")
                        .doesNotContain("email")
                        .doesNotContain("mail");
            }
        }

        @Test
        @DisplayName("RegistrationRequest 不含邮箱验证码字段")
        void registrationRequestShouldNotContainVerificationCode() {
            Field[] fields = com.fons.cloud.ai.rag2okf.common.request.RegistrationRequest.class.getDeclaredFields();

            List<String> fieldNames = Arrays.stream(fields)
                    .map(Field::getName)
                    .toList();

            for (String forbidden : List.of("verificationCode", "verifyCode", "smsCode", "registrationCode")) {
                assertThat(fieldNames)
                        .as("RegistrationRequest 不得包含验证码字段: %s", forbidden)
                        .doesNotContain(forbidden);
            }
        }
    }

    private List<String> collectJavaFiles(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
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
}
