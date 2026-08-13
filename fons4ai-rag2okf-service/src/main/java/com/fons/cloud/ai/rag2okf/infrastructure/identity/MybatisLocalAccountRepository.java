package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fons.cloud.ai.rag2okf.domain.entity.KbUser;
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
    public Optional<KbUser> findByNormalizedEmail(String normalizedEmail) {
        return Optional.ofNullable(userDomainService.getOne(
                Wrappers.<KbUser>lambdaQuery().eq(KbUser::getEmail, normalizedEmail)
        ));
    }

    @Override
    public Optional<KbUser> findByUserKey(String userKey) {
        return Optional.ofNullable(userDomainService.getOne(
                Wrappers.<KbUser>lambdaQuery().eq(KbUser::getUserKey, userKey)
        ));
    }

    @Override
    public void updateLastLoginAt(Long userId, Date loginAt) {
        userDomainService.update(Wrappers.<KbUser>lambdaUpdate()
                .eq(KbUser::getId, userId)
                .set(KbUser::getLastLoginAt, loginAt));
    }

    @Override
    public void updateProfile(KbUser user) {
        userDomainService.update(Wrappers.<KbUser>lambdaUpdate()
                .eq(KbUser::getId, user.getId())
                .set(KbUser::getDisplayName, user.getDisplayName())
                .set(KbUser::getAvatarUrl, user.getAvatarUrl())
                .set(KbUser::getPreferenceJson, user.getPreferenceJson()));
    }

    @Override
    public boolean existsByNormalizedEmail(String normalizedEmail) {
        return userDomainService.count(
                Wrappers.<KbUser>lambdaQuery().eq(KbUser::getEmail, normalizedEmail)
        ) > 0;
    }

    @Override
    public KbUser save(KbUser user) {
        userDomainService.save(user);
        return user;
    }
}
