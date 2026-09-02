package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 任务执行时由用户域安全解析的模型档案引用。 */
@Getter @Setter @Accessors(fluent = true) @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ModelProfileReference {
    /** 模型档案业务键。 */ private String profileKey;
    /** 创建快照时的绑定提示；不保存凭证。 */ private String bindingHint;
}
