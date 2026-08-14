package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.ModelBindingStatus;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.*;

import java.io.Serial;

/**
 * 知识库模型用途绑定持久化实体。
 *
 * <p>绑定表不复制模型档案所有者或 Provider 凭证，避免跨用户凭证泄露和数据漂移。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_model_binding")
public class KbModelBinding extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认非敏感受控参数 JSON。 */
    private static final String DEFAULT_CONFIG_JSON = "{}";

    /** 用途绑定业务标识。 */
    private String bindingKey;

    /** 所属知识库主键。 */
    private Long knowledgeBaseId;

    /** 知识库模型用途。 */
    private ModelUsageType usageType;

    /** 当前用途选中的模型档案主键。 */
    private Long modelProfileId;

    /** 当前用途的非敏感受控参数 JSON。 */
    private String configJson;

    /** 绑定状态。 */
    private ModelBindingStatus status;

    /**
     * 创建模型用途绑定实体。新绑定固定为 ACTIVE 状态，configJson 初始化为空对象。
     *
     * @param knowledgeBaseId 所属知识库主键
     * @param usageType 知识库模型用途
     * @param modelProfileId 选中的模型档案主键
     * @return 已初始化的绑定实体
     */
    public static KbModelBinding create(Long knowledgeBaseId, ModelUsageType usageType, Long modelProfileId) {
        KbModelBinding binding = new KbModelBinding();
        binding.setBindingKey(BusinessKeyGenerator.nextKey());
        binding.setKnowledgeBaseId(knowledgeBaseId);
        binding.setUsageType(usageType);
        binding.setModelProfileId(modelProfileId);
        binding.setConfigJson(DEFAULT_CONFIG_JSON);
        binding.setStatus(ModelBindingStatus.ACTIVE);
        return binding;
    }

}
