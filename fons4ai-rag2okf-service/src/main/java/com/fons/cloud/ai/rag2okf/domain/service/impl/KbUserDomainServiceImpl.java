package com.fons.cloud.ai.rag2okf.domain.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.mapper.KbUserMapper;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import org.springframework.stereotype.Service;

/**
 * @author hongqy
 */
@Service
public class KbUserDomainServiceImpl extends ServiceImpl<KbUserMapper, KbUserEntity> implements KbUserDomainService {
}
