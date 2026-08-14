package com.fons.cloud.ai.rag2okf.common.request.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 当前用户可自行维护的资料请求。
 *
 * @param displayName 展示名称
 * @param avatarUrl 头像地址
 * @param preferenceJson 用户偏好 JSON
 * @author hongqy
 */
public record UpdateUserProfileRequest(
        @Size(max = 64) @Pattern(regexp = "(?s).*\\S.*") String displayName,
        @Size(max = 512) String avatarUrl,
        @Size(max = 8_192) String preferenceJson
) {
}
