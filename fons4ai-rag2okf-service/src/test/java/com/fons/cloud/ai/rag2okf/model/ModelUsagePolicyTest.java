package com.fons.cloud.ai.rag2okf.model;

import com.fons.cloud.ai.rag2okf.common.constants.ModelType;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import com.fons.cloud.ai.rag2okf.domain.service.ModelUsagePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 知识库模型用途与能力类型兼容矩阵测试。
 *
 * @author hongqy
 */
class ModelUsagePolicyTest {

    private final ModelUsagePolicy policy = new ModelUsagePolicy();

    @Test
    void shouldOnlyAllowTheMatchingProfileTypeForEachP0Usage() {
        assertThat(policy.isCompatible(ModelUsageType.ANSWER_GENERATION, ModelType.CHAT)).isTrue();
        assertThat(policy.isCompatible(ModelUsageType.EMBEDDING, ModelType.EMBEDDING)).isTrue();
        assertThat(policy.isCompatible(ModelUsageType.ANSWER_GENERATION, ModelType.EMBEDDING)).isFalse();
        assertThat(policy.isCompatible(ModelUsageType.EMBEDDING, ModelType.CHAT)).isFalse();
    }
}
