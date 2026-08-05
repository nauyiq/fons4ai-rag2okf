package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelConnectionEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbModelConnectionMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelConnectionDomainService;
import org.springframework.stereotype.Service;

/**
 * 用户级 Provider 连接领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbModelConnectionDomainServiceImpl
        extends ServiceImpl<KbModelConnectionMapper, KbModelConnectionEntity>
        implements KbModelConnectionDomainService {
}
