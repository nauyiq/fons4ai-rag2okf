package com.fons.cloud.ai.rag2okf.common.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * 知识库按用途选择模型档案的领域实体。
 *
 * <p>该实体不承载凭证或所有者副本；所有权与用途兼容性由应用服务统一校验。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString
@NoArgsConstructor
@EqualsAndHashCode
public class ModelBinding {

    /** 用途绑定业务标识。 */
    private String bindingKey;

    /** 所属知识库主键。 */
    private Long knowledgeBaseId;

    /** 知识库模型用途。 */
    private String usageType;

    /** 当前用途选中的模型档案主键。 */
    private Long modelProfileId;

    /** 当前用途的非敏感受控参数 JSON。 */
    private String configJson;

    /** 绑定状态。 */
    private String status;
}
