package com.fons.cloud.ai.rag2okf.domain.service.document;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;

import java.util.List;
import java.util.Map;

/**
 * 文档结果领域服务。
 *
 * <p>只服务实体持久化、按业务键查询与 stage CAS 推进，不包装 MinIO、ES
 * 或模型 SDK；实现风格与 {@code KbKnowledgeBaseDomainService} 一致。</p>
 *
 * @author hongqy
 */
public interface KbDocumentResultDomainService extends IService<KbDocumentResult> {

    /**
     * 批量查询每个文档当前的未删除结果。
     *
     * <p>文档存在多个历史结果时，按结果主键倒序选择最新一条，返回结果以文档主键索引。</p>
     *
     * @param documentIds 文档主键集合
     * @return 文档主键到当前结果的索引；无匹配结果时为空 Map
     */
    Map<Long, KbDocumentResult> findCurrentByDocumentIds(List<Long> documentIds);

    /**
     * 根据结果业务标识查询结果记录。
     *
     * @param resultKey 结果业务标识
     * @return 结果记录；不存在或已软删除时返回 {@code null}
     */
    KbDocumentResult findByResultKey(String resultKey);

    /**
     * 查询文档当前结果记录。
     *
     * @param documentId 文档主键
     * @return 最新结果记录；文档尚无结果时返回 {@code null}
     */
    KbDocumentResult findCurrentByDocumentId(Long documentId);

    /**
     * 根据源文件 CAS 令牌查询结果记录。
     *
     * @param sourceFileToken 源文件令牌
     * @return 结果记录；令牌不存在时返回 {@code null}
     */
    KbDocumentResult findBySourceFileToken(String sourceFileToken);

    /**
     * 以 expectedResultVersion 为条件 CAS 推进结果阶段。
     *
     * <p>先校验阶段推进白名单，再执行设计 §4.5 的条件更新：
     * {@code UPDATE kb_document_result SET stage=target, version=version+1
     * WHERE document_id=? AND version=? AND stage=from AND deleted=0}。
     * 版本不匹配、阶段失配或文档无结果时更新行数为 0，视为任务输入过期，
     * 不产生半更新；任何旧任务不得绕过本方法无条件覆盖当前结果。</p>
     *
     * @param resultKey             结果业务标识，限定唯一目标结果
     * @param documentId            文档主键
     * @param expectedResultVersion 调用方持有的期望版本号
     * @param fromStage             期望当前阶段
     * @param toStage               目标阶段
     * @throws DocumentProcessingException 阶段跳跃或条件更新未命中时抛出
     */
    void advanceStage(
            String resultKey, Long documentId, Integer expectedResultVersion,
            ResultStage fromStage, ResultStage toStage);

    /**
     * 以 CAS 原子登记解析制品并推进 INIT→PARSE。
     *
     * @param resultKey 结果业务标识，防止同文档的其他结果版本被误更新
     * @param documentId 文档主键
     * @param expectedResultVersion 期望结果版本
     * @param parsedDocumentObjectKey ParsedDocument 对象键
     * @param parsedMarkdownObjectKey Markdown 对象键，可空
     * @param blockCount 块数量
     * @param warningCount 警告数量
     */
    void commitParsedArtifacts(
            String resultKey,
            Long documentId,
            Integer expectedResultVersion,
            String parsedDocumentObjectKey,
            String parsedMarkdownObjectKey,
            int blockCount,
            int warningCount);

    /**
     * 以 CAS 原子登记分块清单并将结果推进到或保持在 CHUNK。
     *
     * <p>首次分块只允许 PARSE→CHUNK；重新分块允许 CHUNK→CHUNK，已发布结果允许
     * PUBLISH→CHUNK 以显式失效旧投影。条件更新未命中时，调用方必须补偿刚写入的
     * MinIO 清单，旧成功清单不受影响。</p>
     *
     * @param resultKey 结果业务标识
     * @param documentId 文档主键
     * @param expectedResultVersion 期望结果版本
     * @param fromStage 预期当前阶段
     * @param chunkManifestObjectKey 新清单对象键
     * @param parentCount 父块数量
     * @param childCount 子块数量
     * @param totalCount 总块数量
     * @param contentHash 清单内容摘要
     */
    void commitChunkManifest(
            String resultKey, Long documentId, Integer expectedResultVersion, ResultStage fromStage,
            String chunkManifestObjectKey, int parentCount, int childCount, int totalCount,
            String contentHash);
}
