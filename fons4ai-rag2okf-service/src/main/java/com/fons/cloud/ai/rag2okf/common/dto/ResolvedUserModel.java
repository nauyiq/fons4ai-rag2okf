package com.fons.cloud.ai.rag2okf.common.dto;

import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;

/**
 * 当前用户可安全调用的模型档案解析结果。
 *
 * @author hongqy
 */
public record ResolvedUserModel(
        KbModelProfileEntity profile,
        KbModelConnectionEntity connection,
        ResolvedModelDescriptor descriptor
) {
}
