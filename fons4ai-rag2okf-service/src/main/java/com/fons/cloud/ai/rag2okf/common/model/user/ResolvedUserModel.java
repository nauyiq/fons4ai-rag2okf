package com.fons.cloud.ai.rag2okf.common.model.user;

import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;

/**
 * 当前用户可安全调用的模型档案解析结果。
 *
 * @author hongqy
 */
public record ResolvedUserModel(
        KbModelProfile profile,
        KbModelConnection connection,
        ResolvedModelDescriptor descriptor
) {
}
