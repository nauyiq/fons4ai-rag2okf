package com.fons.cloud.ai.rag2okf.infrastructure.identity;

import com.fons.cloud.ai.rag2okf.domain.entity.KbUserEntity;

import java.util.Date;
import java.util.Optional;

/**
 * Rag2OKF 本地账号持久化端口。
 *
 * @author hongqy
 */
public interface LocalAccountRepository {

    /**
     * 按规范化邮箱查询本地账号。
     *
     * @param normalizedEmail trim 且小写后的邮箱
     * @return 本地账号；不存在时为空
     */
    Optional<KbUserEntity> findByNormalizedEmail(String normalizedEmail);

    /**
     * 按 Sa-Token loginId 查询本地账号。
     *
     * @param userKey 不可变业务用户标识
     * @return 本地账号；不存在时为空
     */
    Optional<KbUserEntity> findByUserKey(String userKey);

    /**
     * 更新最近一次成功登录时间。
     *
     * @param userId 本地用户主键
     * @param loginAt 登录成功时间
     */
    void updateLastLoginAt(Long userId, Date loginAt);

    /**
     * 保存用户允许自行维护的资料字段。
     *
     * @param user 用户资料快照，不包含密码摘要更新
     */
    void updateProfile(KbUserEntity user);
}
