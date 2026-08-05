package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbParseRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbParseRevisionMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import org.springframework.stereotype.Service;

/**
 * 解析 Revision 领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbParseRevisionDomainServiceImpl
        extends ServiceImpl<KbParseRevisionMapper, KbParseRevisionEntity>
        implements KbParseRevisionDomainService {
}
