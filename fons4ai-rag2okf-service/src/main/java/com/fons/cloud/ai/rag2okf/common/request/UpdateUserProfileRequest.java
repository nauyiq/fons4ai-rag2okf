package com.fons.cloud.ai.rag2okf.common.request;

/**
 * 当前用户可自行维护的资料请求。
 *
 * @param displayName 展示名称
 * @param avatarUrl 头像地址
 * @param preferenceJson 用户偏好 JSON
 * @author hongqy
 */
public record UpdateUserProfileRequest(String displayName, String avatarUrl, String preferenceJson) {
}
