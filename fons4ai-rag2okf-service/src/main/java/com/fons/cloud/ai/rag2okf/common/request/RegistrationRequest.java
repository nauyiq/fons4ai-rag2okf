package com.fons.cloud.ai.rag2okf.common.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;

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
public record RegistrationRequest(@Email(message = "邮箱格式不正确") String email,
                                  @Size(min = 8, max = 20, message = "密码长度必须在8到20个之间") String password,
                                  @Size(min = 8, max = 20, message = "密码长度必须在8到20个字符之间") String confirmPassword,
                                  @Max(value = 20, message = "展示名称长度必须在20个字符以内") String displayName, boolean termsAccepted) {
}
