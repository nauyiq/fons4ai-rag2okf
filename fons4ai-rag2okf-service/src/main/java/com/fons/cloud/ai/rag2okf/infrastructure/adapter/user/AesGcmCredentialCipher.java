package com.fons.cloud.ai.rag2okf.infrastructure.adapter.user;

import com.fons.cloud.ai.rag2okf.common.exception.user.ModelConfigurationException;
import com.fons.cloud.ai.rag2okf.common.model.user.EncryptedCredential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 基于 AES-GCM 的 API Key 凭证适配器。
 *
 * <p>密钥来源（按优先级）：
 * <ol>
 *   <li>环境变量 {@code sys.security.model-credential-key}（Base64 编码的 32 字节密钥）+
 *       {@code sys.security.model-credential-key-version}：生产环境推荐方式，密钥与密文分离。</li>
 *   <li>从 {@code sys.security.auth-rate-limit-salt}（默认 {@code rag2okf}）派生：
 *       SHA-256(salt) 生成 32 字节 AES-256 密钥，keyVersion 固定为 {@code salt}。
 *       零配置开箱可用，但安全性依赖 salt 的保密性。</li>
 * </ol>
 *
 * <p>注意：方案 2 是轻量折中方案。默认 salt {@code rag2okf} 是已知值，仅防止明文存储 API Key，
 * 不抵御掌握 salt 的攻击者。生产环境应通过环境变量注入独立密钥。
 *
 * @author hongqy
 */
@Component
public class AesGcmCredentialCipher {

    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int NONCE_LENGTH_BYTES = 12;
    private static final int AES_KEY_LENGTH_BYTES = 32;
    /** 从 salt 派生密钥时的固定版本标识。 */
    private static final String DERIVED_KEY_VERSION_PREFIX = "salt:";

    private final String base64Key;
    private final String keyVersion;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * 创建凭证适配器。
     *
     * <p>优先使用外部配置的 {@code model-credential-key}（Base64 32 字节）和
     * {@code model-credential-key-version}；若未配置则从 {@code auth-rate-limit-salt}
     * 派生密钥，实现零配置开箱可用。
     *
     * @param base64Key 部署主密钥的 Base64 编码，空则从 salt 派生
     * @param keyVersion 主密钥版本，空则使用 salt 派生版本
     * @param rateLimitSalt 限流 salt，用于派生密钥
     */
    public AesGcmCredentialCipher(
            @Value("${sys.security.model-credential-key}") String base64Key,
            @Value("${sys.security.model-credential-key-version}") String keyVersion,
            @Value("${sys.security.auth-rate-limit-salt}") String rateLimitSalt
    ) {
        if (base64Key != null && !base64Key.isBlank()) {
            // 外部配置优先
            this.base64Key = base64Key;
            this.keyVersion = keyVersion;
        } else {
            // 从 salt 派生：SHA-256(salt) -> 32 字节 AES-256 密钥
            this.base64Key = Base64.getEncoder().encodeToString(
                    sha256(rateLimitSalt.getBytes(StandardCharsets.UTF_8)));
            this.keyVersion = DERIVED_KEY_VERSION_PREFIX + rateLimitSalt;
        }
    }

    /**
     * 加密 API Key。
     *
     * @param apiKey 原始 Key，仅允许短暂存在于当前调用栈
     * @return 可持久化的加密载荷
     */
    public EncryptedCredential encrypt(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ModelConfigurationException();
        }
        byte[] nonce = new byte[NONCE_LENGTH_BYTES];
        secureRandom.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return new EncryptedCredential(cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8)), nonce, keyVersion());
        } catch (GeneralSecurityException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    /**
     * 解密 API Key。
     *
     * @param credential 可持久化的加密载荷
     * @return 原始 Key，仅供当前调用链使用
     */
    public String decrypt(EncryptedCredential credential) {
        if (credential == null || credential.ciphertext() == null || credential.nonce() == null
                || !keyVersion().equals(credential.keyVersion())) {
            throw new ModelConfigurationException();
        }
        try {
            Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                    new GCMParameterSpec(GCM_TAG_LENGTH_BITS, credential.nonce()));
            return new String(cipher.doFinal(credential.ciphertext()), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    private SecretKey secretKey() {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            if (keyBytes.length != AES_KEY_LENGTH_BYTES) {
                throw new ModelConfigurationException();
            }
            return new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new ModelConfigurationException(exception);
        }
    }

    private String keyVersion() {
        if (keyVersion == null || keyVersion.isBlank()) {
            throw new ModelConfigurationException();
        }
        return keyVersion;
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException e) {
            throw new ModelConfigurationException(e);
        }
    }
}
