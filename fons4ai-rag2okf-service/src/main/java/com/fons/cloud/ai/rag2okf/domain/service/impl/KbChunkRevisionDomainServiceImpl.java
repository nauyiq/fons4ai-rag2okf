package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbChunkRevisionMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import org.springframework.stereotype.Service;

/**
 * 分块 Revision 领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbChunkRevisionDomainServiceImpl
        extends ServiceImpl<KbChunkRevisionMapper, KbChunkRevisionEntity>
        implements KbChunkRevisionDomainService {
}
