package com.fons.cloud.ai.rag2okf.common.request.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelConnectionStatus;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 更新用户 Provider 连接的请求。
 *
 * @param providerName 用户可识别的厂商名称
 * @param displayName 当前用户下唯一的连接展示名称
 * @param baseUrl 模型 API 根地址，必须为安全的 HTTPS 公网地址
 * @param status 连接启用状态，可选；前端不传时不变更
 * @author hongqy
 */
public record UpdateModelConnectionRequest(
        @Size(max = 80) @Pattern(regexp = "(?s).*\\S.*") String providerName,
        @Size(max = 80) @Pattern(regexp = "(?s).*\\S.*") String displayName,
        @Size(max = 512) @Pattern(regexp = "(?s).*\\S.*") String baseUrl,
        ModelConnectionStatus status
) {
}
