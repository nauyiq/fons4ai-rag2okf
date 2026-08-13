package com.fons.cloud.ai.rag2okf.common.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * 本地邮箱密码登录请求。
 *
 * @param email 用户输入的邮箱，服务端会进行规范化处理
 * @param password 原始密码，仅在认证调用链中短暂使用
 * @param rememberMe 是否请求更长的会话有效期
 * @author hongqy
 */
public record LoginRequest(@Email(message = "邮箱格式不正确") String email, @Size(min = 8, max = 20, message = "密码长度必须在8到20个字符之间") String password, boolean rememberMe) {
}
