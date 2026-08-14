package com.fons.cloud.ai.rag2okf.common.model.user;

/**
 * 邮箱密码注册命令。
 *
 * @param email          用户输入邮箱
 * @param password       原始密码，仅在注册调用链中短暂使用
 * @param confirmPassword 确认密码
 * @param displayName    展示名称，空值时使用安全默认值
 * @param clientIp       请求来源地址
 * @author hongqy
 */
public record RegistrationCommand(String email, String password, String confirmPassword, String displayName, String clientIp) {
}
