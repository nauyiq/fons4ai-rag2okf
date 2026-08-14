package com.fons.cloud.ai.rag2okf.domain.service.user.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;
import com.fons.cloud.ai.rag2okf.domain.mapper.user.KbModelProfileMapper;
import com.fons.cloud.ai.rag2okf.domain.service.user.KbModelProfileDomainService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    public List<KbModelProfile> listByProfileKeysAndOwnerUserId(
            Set<String> profileKeys, Long ownerUserId) {
        if (profileKeys == null || profileKeys.isEmpty()) {
            return List.of();
        }
        return list(Wrappers.<KbModelProfile>lambdaQuery()
                .in(KbModelProfile::getProfileKey, profileKeys)
                .eq(KbModelProfile::getOwnerUserId, ownerUserId)
                .eq(KbModelProfile::getDeleted, false));
    }

    @Override
    public Map<Long, String> findProfileKeysByIds(Set<Long> profileIds) {
        if (profileIds == null || profileIds.isEmpty()) {
            return Map.of();
        }
        return listByIds(profileIds).stream()
                .filter(profile -> !Boolean.TRUE.equals(profile.getDeleted()))
                .collect(Collectors.toMap(KbModelProfile::getId, KbModelProfile::getProfileKey));
    }

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
