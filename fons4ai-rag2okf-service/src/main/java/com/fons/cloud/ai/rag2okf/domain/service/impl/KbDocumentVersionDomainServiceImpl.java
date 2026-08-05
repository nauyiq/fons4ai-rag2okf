package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbDocumentVersionEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbDocumentVersionMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbDocumentVersionDomainService;
import org.springframework.stereotype.Service;

/**
 * 文档版本领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbDocumentVersionDomainServiceImpl
        extends ServiceImpl<KbDocumentVersionMapper, KbDocumentVersionEntity>
        implements KbDocumentVersionDomainService {
}
