package com.fons.cloud.ai.rag2okf.domain.service.document;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.common.result.PageResult;

/**
 * 文档身份领域服务。
 *
 * <p>只服务实体持久化、按业务键查询与状态 CAS 更新，不包装 MinIO、ES
 * 或模型 SDK；实现风格与 {@code KbKnowledgeBaseDomainService} 一致。</p>
 *
 * @author hongqy
 */
public interface KbDocumentDomainService extends IService<KbDocument> {

    /**
     * 分页查询知识库下未删除的文档，按最近更新时间和主键倒序排列。
     *
     * @param knowledgeBaseId 知识库主键
     * @param pageNumber      页码，从 1 开始
     * @param pageSize        每页条数
     * @return 文档分页结果
     */
    PageResult<KbDocument> pageActiveByKnowledgeBaseId(
            Long knowledgeBaseId, int pageNumber, int pageSize);

    /**
     * 根据文档业务标识查询活跃文档。
     *
     * @param documentKey 文档业务标识
     * @return 活跃文档；不存在或已软删除时返回 {@code null}
     */
    KbDocument findByDocumentKey(String documentKey);

    /**
     * 以当前状态为条件 CAS 更新用户可见状态。
     *
     * <p>先校验状态白名单，再执行 {@code UPDATE kb_document SET status=target
     * WHERE id=? AND status=from AND deleted=0} 条件更新；状态已被并发修改、
     * 文档不存在或已删除时更新行数为 0，视为任务输入过期失败，
     * 不产生半更新。</p>
     *
     * @param documentId 文档主键
     * @param fromStatus 调用方持有的期望当前状态
     * @param toStatus   目标状态
     * @throws DocumentProcessingException 非法转移或条件更新未命中时抛出
     */
    void casTransitionStatus(Long documentId, DocumentStatus fromStatus, DocumentStatus toStatus);
}
