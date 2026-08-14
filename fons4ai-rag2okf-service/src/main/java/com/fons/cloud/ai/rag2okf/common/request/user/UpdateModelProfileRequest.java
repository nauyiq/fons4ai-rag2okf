package com.fons.cloud.ai.rag2okf.common.request.user;

import com.fons.cloud.ai.rag2okf.common.constants.user.ModelProfileStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 更新用户模型档案的请求。
 *
 * @param modelName 厂商实际模型 ID
 * @param dimensions 向量维度提示，仅 EMBEDDING 可设置
 * @param contextWindowLength 上下文窗口长度，由前端维护，NULL 表示未设置
 * @param timeoutSeconds 受控请求超时秒数，范围 1 到 120
 * @param temperature 受控对话温度，仅 LLM 可设置
 * @param status 档案启用状态
 * @author hongqy
 */
public record UpdateModelProfileRequest(
        @Size(max = 160) @Pattern(regexp = "(?s).*\\S.*") String modelName,
        @Positive Integer dimensions,
        @Min(1) @Max(2_000_000) Integer contextWindowLength,
        @Min(1) @Max(120) Integer timeoutSeconds,
        @DecimalMin("0.0") @DecimalMax("2.0") Double temperature,
        ModelProfileStatus status
) {
}
