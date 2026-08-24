package com.fons.cloud.ai.rag2okf.domain.service.document;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.common.constants.document.ResultStage;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocumentResult;

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
     * @param documentId            文档主键
     * @param expectedResultVersion 调用方持有的期望版本号
     * @param fromStage             期望当前阶段
     * @param toStage               目标阶段
     * @throws DocumentProcessingException 阶段跳跃或条件更新未命中时抛出
     */
    void advanceStage(Long documentId, Integer expectedResultVersion, ResultStage fromStage, ResultStage toStage);
}
