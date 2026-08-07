package com.fons.cloud.ai.rag2okf.common.request;

/**
 * 邮箱密码注册请求。
 *
 * @param email          用户输入的邮箱，服务端会进行规范化处理
 * @param password       原始密码，仅在注册调用链中短暂使用
 * @param confirmPassword 确认密码，需与 password 一致
 * @param displayName    展示名称，空值时使用安全默认值
 * @param termsAccepted  是否同意条款，必须为 true
 * @author hongqy
 */
public record RegistrationRequest(String email, String password, String confirmPassword, String displayName, boolean termsAccepted) {
}
