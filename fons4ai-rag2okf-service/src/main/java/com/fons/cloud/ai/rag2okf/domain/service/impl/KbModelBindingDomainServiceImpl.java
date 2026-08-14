package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbModelBinding;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbModelBindingMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbModelBindingDomainService;
import org.springframework.stereotype.Service;

/**
 * 知识库模型用途绑定领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbModelBindingDomainServiceImpl
        extends ServiceImpl<KbModelBindingMapper, KbModelBinding>
        implements KbModelBindingDomainService {
}
