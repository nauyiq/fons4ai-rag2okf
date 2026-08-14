package com.fons.cloud.ai.rag2okf.common.model.user;

/**
 * API Key 加密后的持久化载荷。
 *
 * @param ciphertext AES-GCM 密文
 * @param nonce 与本次密文绑定的随机 nonce
 * @param keyVersion 部署主密钥版本
 * @author hongqy
 */
public record EncryptedCredential(byte[] ciphertext, byte[] nonce, String keyVersion) {
}
