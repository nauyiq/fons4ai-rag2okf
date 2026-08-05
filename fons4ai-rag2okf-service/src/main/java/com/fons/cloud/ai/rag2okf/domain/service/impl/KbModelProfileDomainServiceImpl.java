package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelProfileEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbModelProfileMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelProfileDomainService;
import org.springframework.stereotype.Service;

/**
 * 用户模型档案领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbModelProfileDomainServiceImpl
        extends ServiceImpl<KbModelProfileMapper, KbModelProfileEntity>
        implements KbModelProfileDomainService {
}
