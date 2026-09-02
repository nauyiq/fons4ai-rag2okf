package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import lombok.Getter;

/**
 * 分块内容边界策略。
 *
 * <p>每个值都带有显式持久化代码，禁止依赖枚举声明顺序或字符串分支表达策略语义。</p>
 *
 * @author hongqy
 */
@Getter
public enum ChunkBoundaryType {

    /** 通过 LangChain4j 递归 splitter 按段落、句子、词和字符逐级切分。 */
    RECURSIVE("RECURSIVE"),
    /** 通过 Fons4AI LangChain4j Markdown 扩展按标题层级切分。 */
    MARKDOWN_HEADER("MARKDOWN_HEADER"),
    /** 按相邻文本向量相似度切分，必须具备有效的 EMBEDDING 档案。 */
    SEMANTIC("SEMANTIC");

    /** 与请求快照和数据库列一致的稳定代码。
     * -- GETTER --
     *  获取稳定代码。
     *
     * @return 用于快照、持久化和对外契约的代码
     */
    @EnumValue
    private final String value;

    ChunkBoundaryType(String value) {
        this.value = value;
    }

    /**
     * 按稳定代码严格解析策略。
     *
     * @param value 请求或快照中的策略代码
     * @return 对应策略
     * @throws DocumentProcessingException 代码为空或未知时抛出分块策略非法错误
     */
    public static ChunkBoundaryType fromValue(String value) {
        for (ChunkBoundaryType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new DocumentProcessingException(Rag2OkfResultCode.CHUNK_POLICY_INVALID);
    }
}
