package com.fons.cloud.ai.rag2okf.application.model;

/**
 * 用户模型 API Key 的加解密端口。
 *
 * @author hongqy
 */
public interface CredentialCipher {

    /**
     * 加密 API Key。
     *
     * @param apiKey 原始 Key，仅允许短暂存在于当前调用栈
     * @return 可持久化的加密载荷
     */
    EncryptedCredential encrypt(String apiKey);

    /**
     * 解密 API Key。
     *
     * @param credential 可持久化的加密载荷
     * @return 原始 Key，仅供当前调用链使用
     */
    String decrypt(EncryptedCredential credential);
}
