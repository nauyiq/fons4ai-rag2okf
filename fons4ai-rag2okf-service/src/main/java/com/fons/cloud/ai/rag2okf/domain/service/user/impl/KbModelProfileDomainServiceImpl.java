package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbModelProfileMapper;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户模型档案领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbModelProfileDomainServiceImpl
        extends ServiceImpl<KbModelProfileMapper, KbModelProfile>
        implements KbModelProfileDomainService {

    @Override
    public List<KbModelProfile> listByOwnerUserId(Long ownerUserId) {
        return list(Wrappers.<KbModelProfile>lambdaQuery()
                .eq(KbModelProfile::getOwnerUserId, ownerUserId)
                .eq(KbModelProfile::getDeleted, false)
                .orderByDesc(KbModelProfile::getUpdated));
    }

    @Override
    public List<KbModelProfile> listByOwnerUserIdAndConnectionId(Long ownerUserId, Long connectionId) {
        return list(Wrappers.<KbModelProfile>lambdaQuery()
                .eq(KbModelProfile::getOwnerUserId, ownerUserId)
                .eq(KbModelProfile::getConnectionId, connectionId)
                .eq(KbModelProfile::getDeleted, false)
                .orderByDesc(KbModelProfile::getUpdated));
    }

    @Override
    public KbModelProfile findByProfileKeyAndOwnerUserId(String profileKey, Long ownerUserId) {
        return getOne(Wrappers.<KbModelProfile>lambdaQuery()
                .eq(KbModelProfile::getProfileKey, profileKey)
                .eq(KbModelProfile::getOwnerUserId, ownerUserId)
                .eq(KbModelProfile::getDeleted, false));
    }

    @Override
    public boolean removeByConnectionIdAndOwnerUserId(Long connectionId, Long ownerUserId) {
        return remove(Wrappers.<KbModelProfile>lambdaQuery()
                .eq(KbModelProfile::getConnectionId, connectionId)
                .eq(KbModelProfile::getOwnerUserId, ownerUserId)
                .eq(KbModelProfile::getDeleted, false));
    }
}
