package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;

/**
 * 分块层级组织策略。
 *
 * <p>层级策略与 {@link ChunkBoundaryType} 正交；一个策略只表达块关系，不改变边界切分语义。</p>
 */
public enum ChunkHierarchyType {

    /** 所有块平铺输出，不建立父子关系。 */
    FLAT("FLAT"),
    /** 以父块承载结构上下文、子块承载可嵌入内容。 */
    PARENT_CHILD("PARENT_CHILD");

    /** 与请求快照和数据库列一致的稳定代码。 */
    @EnumValue
    private final String value;

    ChunkHierarchyType(String value) {
        this.value = value;
    }

    /**
     * 获取稳定代码。
     *
     * @return 用于快照、持久化和对外契约的代码
     */
    public String getValue() {
        return value;
    }

    /**
     * 按稳定代码严格解析策略。
     *
     * @param value 请求或快照中的策略代码
     * @return 对应策略
     * @throws DocumentProcessingException 代码为空或未知时抛出分块策略非法错误
     */
    public static ChunkHierarchyType fromValue(String value) {
        for (ChunkHierarchyType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
    }
}
