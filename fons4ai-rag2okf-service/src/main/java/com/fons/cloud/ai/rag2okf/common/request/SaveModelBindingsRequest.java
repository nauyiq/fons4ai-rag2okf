package com.fons.cloud.ai.rag2okf.common.request;

import jakarta.validation.Valid;

import java.util.List;

/**
 * 保存知识库模型绑定请求。
 *
 * <p>整体替换指定知识库的全部模型用途绑定；同一用途最多一个有效绑定。</p>
 *
 * @param modelBindings 模型用途绑定列表
 * @author hongqy
 */
public record SaveModelBindingsRequest(
        @Valid
        List<ModelBindingItem> modelBindings
) {
}
