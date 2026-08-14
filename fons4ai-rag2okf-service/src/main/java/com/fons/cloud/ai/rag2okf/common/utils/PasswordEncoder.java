package com.fons.cloud.ai.rag2okf.common.utils;

import org.springframework.security.crypto.factory.PasswordEncoderFactories;

/**
 * 用户密码摘要工具，统一封装密码散列与匹配逻辑。
 *
 * @author hongqy
 */
public final class PasswordEncoder {

    private static final org.springframework.security.crypto.password.PasswordEncoder PASSWORD_ENCODER =
            PasswordEncoderFactories.createDelegatingPasswordEncoder();

    private PasswordEncoder() {
    }

    /**
     * 使用框架委托编码器生成密码摘要。
     *
     * @param rawPassword 原始密码，仅允许存在于当前调用栈
     * @return 带算法标识的密码摘要
     */
    public static String hash(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * 校验原始密码是否匹配已保存摘要。
     *
     * @param rawPassword 原始密码
     * @param passwordHash 已保存的密码摘要
     * @return 摘要存在且密码匹配时返回 {@code true}
     */
    public static boolean matches(String rawPassword, String passwordHash) {
        return passwordHash != null && PASSWORD_ENCODER.matches(rawPassword, passwordHash);
    }
}
