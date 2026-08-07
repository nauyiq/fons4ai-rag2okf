package com.fons.cloud.ai.rag2okf.common.dto;

/**
 * 本地邮箱密码登录命令。
 *
 * @param email 用户输入邮箱
 * @param password 用户输入密码，仅在认证调用期间使用
 * @param rememberMe 是否请求更长的会话有效期
 * @param clientIp 请求来源地址
 * @author hongqy
 */
public record LoginCommand(String email, String password, boolean rememberMe, String clientIp) {
}
