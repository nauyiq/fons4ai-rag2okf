package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;
import com.fons.cloud.ai.rag2okf.domain.service.KbUserDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

/**
 * 基于 MyBatis-Plus 的本地账号持久化适配器。
 *
 * @author hongqy
 */
@Repository
@RequiredArgsConstructor
public class MybatisLocalAccountRepository implements LocalAccountRepository {

    private final KbUserDomainService userDomainService;

    @Override
    public Optional<KbUserEntity> findByNormalizedEmail(String normalizedEmail) {
        return Optional.ofNullable(userDomainService.getOne(
                Wrappers.<KbUserEntity>lambdaQuery().eq(KbUserEntity::getEmail, normalizedEmail)
        ));
    }

    @Override
    public Optional<KbUserEntity> findByUserKey(String userKey) {
        return Optional.ofNullable(userDomainService.getOne(
                Wrappers.<KbUserEntity>lambdaQuery().eq(KbUserEntity::getUserKey, userKey)
        ));
    }

    @Override
    public void updateLastLoginAt(Long userId, Date loginAt) {
        userDomainService.update(Wrappers.<KbUserEntity>lambdaUpdate()
                .eq(KbUserEntity::getId, userId)
                .set(KbUserEntity::getLastLoginAt, loginAt));
    }

    @Override
    public void updateProfile(KbUserEntity user) {
        userDomainService.update(Wrappers.<KbUserEntity>lambdaUpdate()
                .eq(KbUserEntity::getId, user.getId())
                .set(KbUserEntity::getDisplayName, user.getDisplayName())
                .set(KbUserEntity::getAvatarUrl, user.getAvatarUrl())
                .set(KbUserEntity::getPreferenceJson, user.getPreferenceJson()));
    }
}
