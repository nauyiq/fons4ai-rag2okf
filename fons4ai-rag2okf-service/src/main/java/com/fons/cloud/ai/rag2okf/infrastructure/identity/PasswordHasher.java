package com.fons.cloud.ai.rag2okf.infrastructure.identity;

/**
 * 本地账号密码摘要的领域抽象。
 *
 * @author hongqy
 */
public interface PasswordHasher {

    /**
     * 生成可验证且带算法标识的密码摘要。
     *
     * @param rawPassword 原始密码，仅允许在调用栈内短暂存在
     * @return 密码摘要
     */
    String hash(String rawPassword);

    /**
     * 校验原始密码与已有摘要是否匹配。
     *
     * @param rawPassword 原始密码
     * @param passwordHash 已有密码摘要
     * @return 是否匹配
     */
    boolean matches(String rawPassword, String passwordHash);
}
