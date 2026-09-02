package com.fons.cloud.ai.rag2okf.common.constants.document;

/** 分块在清单中的语义角色。 */
public enum ChunkRole {
    /** 平铺策略输出的独立可嵌入块。 */
    FLAT,
    /** 父子策略中承载结构上下文的父块，默认不参与嵌入。 */
    PARENT,
    /** 父子策略中实际参与嵌入的内容子块。 */
    CHILD
}
