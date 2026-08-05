package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbOutboxEventEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbOutboxEventMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbOutboxEventDomainService;
import org.springframework.stereotype.Service;

/**
 * 事务 Outbox 事件领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbOutboxEventDomainServiceImpl
        extends ServiceImpl<KbOutboxEventMapper, KbOutboxEventEntity>
        implements KbOutboxEventDomainService {
}
