package com.fons.cloud.ai.rag2okf.domain.service.document.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.domain.entity.document.KbDocument;
import com.fons.cloud.ai.rag2okf.domain.mapper.document.KbDocumentMapper;
import com.fons.cloud.ai.rag2okf.domain.service.document.KbDocumentDomainService;
import com.fons.cloud.common.result.PageResult;
import org.springframework.stereotype.Service;

/**
 * 文档身份领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbDocumentDomainServiceImpl extends ServiceImpl<KbDocumentMapper, KbDocument> implements KbDocumentDomainService {

    @Override
    public PageResult<KbDocument> pageActiveByKnowledgeBaseId(
            Long knowledgeBaseId, int pageNumber, int pageSize) {
        Page<KbDocument> page = new Page<>(pageNumber, pageSize);
        this.page(page, Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KbDocument::getDeleted, false)
                .orderByDesc(KbDocument::getUpdated)
                .orderByDesc(KbDocument::getId));
        PageResult<KbDocument> result = new PageResult<>(
                pageNumber, pageSize, page.getTotal(), page.getRecords());
        result.setPages((int) page.getPages());
        return result;
    }

    @Override
    public KbDocument findByDocumentKey(String documentKey) {
        return this.getOne(Wrappers.<KbDocument>lambdaQuery()
                .eq(KbDocument::getDeleted, false)
                .eq(KbDocument::getDocumentKey, documentKey));
    }

    @Override
    public void casTransitionStatus(Long documentId, DocumentStatus fromStatus, DocumentStatus toStatus) {
        if (!fromStatus.canTransitionTo(toStatus)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        boolean updated = this.update(Wrappers.<KbDocument>lambdaUpdate()
                .set(KbDocument::getStatus, toStatus)
                .eq(KbDocument::getId, documentId)
                .eq(KbDocument::getStatus, fromStatus)
                .eq(KbDocument::getDeleted, false));
        if (!updated) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
    }
}
