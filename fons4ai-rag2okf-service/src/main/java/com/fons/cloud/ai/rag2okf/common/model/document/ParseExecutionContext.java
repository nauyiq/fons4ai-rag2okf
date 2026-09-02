package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * 解析执行上下文。
 *
 * <p>只携带任务快照冻结的业务事实、已登记源对象引用和可重复打开的受控读取器；
 * 不包含 Base URL、API Key 或连接密文。每次打开的输入流由调用方负责关闭。</p>
 */
@Getter
@Accessors(fluent = true)
@EqualsAndHashCode(exclude = "sourceStreamProvider")
public class ParseExecutionContext {

    /** 工作空间业务标识。 */
    private final String workspaceKey;
    /** 知识库业务标识。 */
    private final String knowledgeBaseKey;
    /** 文档业务标识。 */
    private final String documentKey;
    /** 当前解析结果业务标识。 */
    private final String resultKey;
    /** 当前解析结果序号。 */
    private final int resultVersion;
    /** 发起解析的用户主键。 */
    private final Long ownerUserId;
    /** 已登记源文件令牌。 */
    private final String sourceFileToken;
    /** 源文件对象存储键。 */
    private final String sourceObjectKey;
    /** 原始文件名。 */
    private final String filename;
    /** 服务端确认的文件内容类型。 */
    private final String contentType;
    /** 源文件 SHA-256 摘要。 */
    private final String sha256;
    /** 本次任务冻结的解析器类型。 */
    private final ParserType parserType;
    /** 按能力类型索引的非秘密模型档案引用。 */
    private final Map<String, ModelProfileReference> modelProfileRefs;
    /** 可重复打开的受控源文件读取器。 */
    private final DocumentSourceStreamProvider sourceStreamProvider;

    public ParseExecutionContext(String workspaceKey, String knowledgeBaseKey, String documentKey,
                                 String resultKey, int resultVersion, Long ownerUserId,
                                 String sourceFileToken, String sourceObjectKey, String filename,
                                 String contentType, String sha256, ParserType parserType,
                                 Map<String, ModelProfileReference> modelProfileRefs,
                                 DocumentSourceStreamProvider sourceStreamProvider) {
        this.workspaceKey = workspaceKey;
        this.knowledgeBaseKey = knowledgeBaseKey;
        this.documentKey = Objects.requireNonNull(documentKey, "documentKey must not be null");
        this.resultKey = Objects.requireNonNull(resultKey, "resultKey must not be null");
        this.resultVersion = resultVersion;
        this.ownerUserId = Objects.requireNonNull(ownerUserId, "ownerUserId must not be null");
        this.sourceFileToken = Objects.requireNonNull(sourceFileToken, "sourceFileToken must not be null");
        this.sourceObjectKey = Objects.requireNonNull(sourceObjectKey, "sourceObjectKey must not be null");
        this.filename = Objects.requireNonNull(filename, "filename must not be null");
        this.contentType = Objects.requireNonNull(contentType, "contentType must not be null");
        this.sha256 = Objects.requireNonNull(sha256, "sha256 must not be null");
        this.parserType = Objects.requireNonNull(parserType, "parserType must not be null");
        this.modelProfileRefs = modelProfileRefs == null ? Map.of() : Map.copyOf(modelProfileRefs);
        this.sourceStreamProvider = Objects.requireNonNull(
                sourceStreamProvider, "sourceStreamProvider must not be null");
    }

    /**
     * 打开一条从源文件首字节开始的独立输入流。
     *
     * @return 新的受控源文件输入流，调用方负责关闭
     */
    public InputStream openSourceStream() {
        return Objects.requireNonNull(sourceStreamProvider.openStream(), "source stream must not be null");
    }
}
