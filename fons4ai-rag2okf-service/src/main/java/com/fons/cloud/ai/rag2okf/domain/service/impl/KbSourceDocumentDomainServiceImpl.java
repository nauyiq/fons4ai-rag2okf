package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbSourceDocumentMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import org.springframework.stereotype.Service;

/**
 * 源文档领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbSourceDocumentDomainServiceImpl
        extends ServiceImpl<KbSourceDocumentMapper, KbSourceDocumentEntity>
        implements KbSourceDocumentDomainService {
}
