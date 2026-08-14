package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbModelConnectionMapper;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelConnectionDomainService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户级 Provider 连接领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbModelConnectionDomainServiceImpl
        extends ServiceImpl<KbModelConnectionMapper, KbModelConnection>
        implements KbModelConnectionDomainService {

    @Override
    public List<KbModelConnection> listByOwnerUserId(Long ownerUserId) {
        return list(Wrappers.lambdaQuery(KbModelConnection.class)
                .eq(KbModelConnection::getOwnerUserId, ownerUserId)
                .eq(KbModelConnection::getDeleted, false)
                .orderByDesc(KbModelConnection::getUpdated));
    }

    @Override
    public KbModelConnection findByConnectionKeyAndOwnerUserId(String connectionKey, Long ownerUserId) {
        return getOne(Wrappers.lambdaQuery(KbModelConnection.class)
                .eq(KbModelConnection::getConnectionKey, connectionKey)
                .eq(KbModelConnection::getOwnerUserId, ownerUserId)
                .eq(KbModelConnection::getDeleted, false));
    }

    @Override
    public KbModelConnection findByIdAndOwnerUserId(Long connectionId, Long ownerUserId) {
        return getOne(Wrappers.lambdaQuery(KbModelConnection.class)
                .eq(KbModelConnection::getId, connectionId)
                .eq(KbModelConnection::getOwnerUserId, ownerUserId)
                .eq(KbModelConnection::getDeleted, false));
    }
}
