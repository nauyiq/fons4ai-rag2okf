package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbPublicationRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbPublicationRevisionMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbPublicationRevisionDomainService;
import org.springframework.stereotype.Service;

/**
 * 发布 Revision 领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbPublicationRevisionDomainServiceImpl
        extends ServiceImpl<KbPublicationRevisionMapper, KbPublicationRevisionEntity>
        implements KbPublicationRevisionDomainService {
}
