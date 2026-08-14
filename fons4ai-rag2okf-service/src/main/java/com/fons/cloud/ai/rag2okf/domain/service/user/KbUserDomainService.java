package com.fons.cloud.ai.rag2okf.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserProfileAggregate;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;

/**
 * 本地用户领域服务，负责用户实体查询、资料聚合恢复和用户资料持久化协调。
 *
 * <p>保留 MyBatis-Plus {@link IService} 通用能力；应用层优先调用本接口声明的业务语义方法。</p>
 *
 * @author hongqy
 */
public interface KbUserDomainService extends IService<KbUser> {

    /**
     * 根据不可变用户业务标识查找用户。
     *
     * @param userKey 用户业务标识
     * @return 用户；不存在时返回 {@code null}
     */
    KbUser findByUserKey(String userKey);

    /**
     * 按当前会话用户标识恢复用户资料聚合。
     *
     * <p>查询仅在用户域内组合用户、其个人工作空间和有效成员关系；用户不存在时返回
     * {@code null}，个人工作空间或成员关系不存在时保留为空。</p>
     *
     * @param userKey 不可变用户业务标识
     * @return 用户资料聚合；用户不存在时返回 {@code null}
     */
    UserProfileAggregate findUserProfileAggregate(String userKey);

    /**
     * 根据邮箱查找用户
     * @param email 邮箱
     * @return 用户
     */
    KbUser findByEmail(String email);

    /**
     * 持久化用户允许自行维护的资料白名单字段。
     *
     * <p>只更新展示名称、头像地址和偏好 JSON，不触碰邮箱、密码、状态和业务标识。</p>
     *
     * @param user 已完成资料规则处理的用户实体
     */
    void updateProfile(KbUser user);

    /**
     * 更新用户最后登录时间
     * @param id 用户ID
     */
    void updateLastLoginAt(Long id);
}
