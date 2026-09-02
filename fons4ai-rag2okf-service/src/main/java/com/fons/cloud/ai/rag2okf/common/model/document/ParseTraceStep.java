package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * 解析流程中的单个可观测步骤。
 *
 * @author hongqy
 */
@Getter @Setter @Accessors(fluent = true) @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ParseTraceStep {
    /** 成功完成的步骤状态。 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 未测量外部调用耗时时使用的零值。 */
    public static final long UNKNOWN_DURATION_MILLIS = 0L;

    /** 步骤名称。 */ private String step;
    /** 使用的模型能力；本地步骤为空。 */ private String capability;
    /** 安全模型档案标识；本地步骤为空。 */ private String profileKey;
    /** 执行状态：SUCCESS、WARNING 或 FAILED。 */ private String status;
    /** 耗时，单位毫秒。 */ private long durationMs;
    /** 外部调用链追踪标识；不含敏感请求内容。 */ private String externalTraceId;
}
