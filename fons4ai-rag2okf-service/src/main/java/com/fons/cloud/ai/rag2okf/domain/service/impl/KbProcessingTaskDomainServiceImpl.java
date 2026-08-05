package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbProcessingTaskEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbProcessingTaskMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbProcessingTaskDomainService;
import org.springframework.stereotype.Service;

/**
 * 异步处理任务领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbProcessingTaskDomainServiceImpl
        extends ServiceImpl<KbProcessingTaskMapper, KbProcessingTaskEntity>
        implements KbProcessingTaskDomainService {
}
