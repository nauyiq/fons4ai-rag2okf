package com.fons.cloud.ai.rag2okf.domain.entity.document;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 文档结果生命周期、源文件指针与制品登记持久化实体。
 *
 * <p>一张表承载解析/分块/发布全生命周期：靠 {@code stage} 按
 * INIT→PARSE→CHUNK→PUBLISH 顺序推进，MinIO 制品对象键与解析/分块统计
 * 随阶段推进写入。失败原因不在本表，统一归 {@code kb_processing_task}。</p>
 *
 * <p>阶段推进规则下沉到 {@link #advanceStage}，配套领域服务以
 * {@code expectedResultVersion} 做 CAS 条件更新，防止旧任务无条件覆盖当前结果。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document_result")
public class KbDocumentResult extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 结果业务标识，每次新建结果时生成。 */
    private String resultKey;

    /** 所属文档主键。 */
    private Long documentId;

    /** 当前源文件 CAS 令牌，每次上传重新生成。 */
    private String sourceFileToken;

    /** 当前源文件 MinIO 对象键，结构为 sources/{fileToken}/{sanitizedFilename}。 */
    private String sourceObjectKey;

    /** 上传时原始文件名。 */
    private String sourceOriginalFilename;

    /** 服务端校验后的 MIME 类型。 */
    private String sourceContentType;

    /** 源文件字节数，流式写入时统计。 */
    private Long sourceSizeBytes;

    /** 源文件 SHA-256 摘要，流式写入时计算。 */
    private String sourceSha256;

    /** 上传用户主键。 */
    private Long sourceUploadActorId;

    /** 结果生命周期阶段，只允许顺序推进。 */
    private ResultStage stage;

    /** 解析器类型，INIT 阶段为空，PARSE 任务创建后冻结。 */
    private ParserType parserType;

    /** 任务创建时冻结的解析器、模型引用与非秘密参数快照 JSON。 */
    private String parserSnapshotJson;

    /** ParsedDocument v1 JSON 的 MinIO 对象键，解析成功并通过校验后写入。 */
    private String parsedDocumentObjectKey;

    /** 派生 Markdown 的 MinIO 对象键，可选。 */
    private String parsedMarkdownObjectKey;

    /** 结构块数量，解析完成时写入。 */
    private Integer blockCount;

    /** 解析警告数量，解析完成时写入。 */
    private Integer warningCount;

    /** 分块边界策略白名单：LENGTH、STRUCTURE、SEMANTIC，PARSE 后写入。 */
    private String boundaryType;

    /** 分块层级策略白名单：FLAT、PARENT_CHILD，PARSE 后写入。 */
    private String hierarchyType;

    /** 任务创建时冻结的分块参数与语义模型引用快照 JSON。 */
    private String policySnapshotJson;

    /** ChunkManifest v1 的 MinIO 对象键，分块成功并校验后写入。 */
    private String chunkManifestObjectKey;

    /** 父分块数量。 */
    private Integer parentCount;

    /** 子分块数量。 */
    private Integer childCount;

    /** 分块总数。 */
    private Integer totalCount;

    /** 规范化分块集合内容摘要，分块成功时计算。 */
    private String contentHash;

    /** 向量模型档案业务标识引用，发布时冻结。 */
    private String embeddingProfileKey;

    /** 向量实际验证维度，模型返回校验后写入。 */
    private Integer embeddingDimensions;

    /** ES 投影安全引用 JSON，writeProjection 成功后写入。 */
    private String projectionRefJson;

    /** 投影文档数，validateWrite 成功后写入。 */
    private Integer projectionCount;

    /** 发布成功时间。 */
    private Date publishedAt;

    /**
     * 源文件指针六元组。
     *
     * <p>上传链路完成安全预检与 MinIO 流式写入后构造：fileToken 为本次上传的
     * CAS 令牌，size 与 sha256 来自流式统计，objectKey 由制品服务按
     * {@code sources/{fileToken}/{sanitizedFilename}} 结构生成。</p>
     *
     * @param fileToken        源文件 CAS 令牌
     * @param objectKey        源文件 MinIO 对象键
     * @param originalFilename 上传时原始文件名
     * @param contentType      服务端校验后的 MIME 类型
     * @param sizeBytes        源文件字节数
     * @param sha256           源文件 SHA-256 摘要
     */
    public record SourceFilePointer(
            String fileToken,
            String objectKey,
            String originalFilename,
            String contentType,
            Long sizeBytes,
            String sha256) {
    }

    /**
     * 创建结果实体。
     *
     * <p>上传完成时以 {@code stage=INIT} 建立结果底座，携带源文件指针六元组
     * 与上传用户；解析器类型、制品对象键与统计字段随后续阶段推进写入。</p>
     *
     * @param documentId     所属文档主键
     * @param uploadActorId  上传用户主键
     * @param sourceFile     源文件指针六元组
     * @return 已初始化的结果实体
     */
    public static KbDocumentResult create(Long documentId, Long uploadActorId, SourceFilePointer sourceFile) {
        KbDocumentResult result = new KbDocumentResult();
        result.setResultKey(BusinessKeyGenerator.nextKey());
        result.setDocumentId(documentId);
        result.setSourceFileToken(sourceFile.fileToken());
        result.setSourceObjectKey(sourceFile.objectKey());
        result.setSourceOriginalFilename(sourceFile.originalFilename());
        result.setSourceContentType(sourceFile.contentType());
        result.setSourceSizeBytes(sourceFile.sizeBytes());
        result.setSourceSha256(sourceFile.sha256());
        result.setSourceUploadActorId(uploadActorId);
        result.setStage(ResultStage.INIT);
        return result;
    }

    /**
     * 按白名单推进结果阶段。
     *
     * <p>先核对当前阶段与调用方期望一致，再校验顺序推进白名单；阶段跳跃
     *（如 INIT→CHUNK）或期望阶段失配均拒绝。持久化时由领域服务以
     * {@code expectedResultVersion} 条件更新保证并发安全。</p>
     *
     * @param expectStage 调用方持有的期望当前阶段
     * @param targetStage 目标阶段
     * @throws DocumentProcessingException 阶段跳跃或期望失配时抛出，语义为任务输入已不是当前内容
     */
    public void advanceStage(ResultStage expectStage, ResultStage targetStage) {
        if (this.stage != expectStage || !expectStage.canAdvanceTo(targetStage)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        this.stage = targetStage;
    }
}
