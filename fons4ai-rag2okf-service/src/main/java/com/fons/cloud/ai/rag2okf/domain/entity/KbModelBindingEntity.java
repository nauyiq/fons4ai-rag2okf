package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.ModelUsageType;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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
public class KbModelBindingEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

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
    private String status;
}
