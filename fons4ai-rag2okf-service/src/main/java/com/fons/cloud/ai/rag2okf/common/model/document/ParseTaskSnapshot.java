package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * PARSE 任务执行所需的冻结输入。
 *
 * <p>只保存业务键、非秘密参数和模型档案引用；不保存连接地址、API Key 或密文。</p>
 */
@Getter @Setter @Accessors(fluent = true) @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode
public class ParseTaskSnapshot {
    /** 所属工作空间业务键。 */ private String workspaceKey;
    /** 所属知识库业务键。 */ private String knowledgeBaseKey;
    /** 目标文档业务键。 */ private String documentKey;
    /** 发起时的源文件令牌，用于执行前 CAS 校验。 */ private String sourceFileToken;
    /** 解析器类型。 */ private ParserType parserType;
    /** 解析器非秘密参数。 */ private Map<String, Object> parserOptions = new LinkedHashMap<>();
    /** 冻结的分块策略。 */ private ChunkPolicy chunkPolicy;
    /** 按能力名称索引的安全模型档案引用。 */ private Map<String, ModelProfileReference> modelProfileRefs = new LinkedHashMap<>();
    /** 解析与首次分块成功后是否继续发布。 */ private boolean publishAfterSuccess;
    /** 发起用户数据库主键。 */ private Long requestedBy;
    /** 快照创建时间。 */ private Date requestedAt;

    /** 创建任务快照。 */
    public static ParseTaskSnapshot of(String workspaceKey, String knowledgeBaseKey, String documentKey,
                                       String sourceFileToken, ParserType parserType,
                                       Map<String, Object> parserOptions, ChunkPolicy chunkPolicy,
                                       Map<String, ModelProfileReference> modelProfileRefs,
                                       boolean publishAfterSuccess, Long requestedBy, Date requestedAt) {
        return new ParseTaskSnapshot(workspaceKey, knowledgeBaseKey, documentKey, sourceFileToken, parserType,
                parserOptions == null ? new LinkedHashMap<>() : new LinkedHashMap<>(parserOptions), chunkPolicy,
                modelProfileRefs == null ? new LinkedHashMap<>() : new LinkedHashMap<>(modelProfileRefs),
                publishAfterSuccess, requestedBy, requestedAt == null ? null : new Date(requestedAt.getTime()));
    }

    /** @return 发起时间的防御性副本。 */
    public Date requestedAt() {
        return requestedAt == null ? null : new Date(requestedAt.getTime());
    }
}
