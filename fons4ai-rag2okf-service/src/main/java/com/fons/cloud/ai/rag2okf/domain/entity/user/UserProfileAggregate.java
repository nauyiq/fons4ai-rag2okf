package com.fons.cloud.ai.rag2okf.domain.entity.user;

import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;

/**
 * 当前用户资料聚合，集中表达用户、个人工作空间及其成员关系的只读组合。
 *
 * <p>用户必须存在；历史数据尚未完成个人空间初始化时，工作空间和成员关系允许为空。</p>
 *
 * @param user 当前本地用户
 * @param workspace 用户拥有的个人工作空间，可为空
 * @param membership 用户在个人工作空间中的有效成员关系，可为空
 * @author hongqy
 */
public record UserProfileAggregate(
        KbUser user,
        KbWorkspace workspace,
        KbWorkspaceMember membership
) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 校验聚合中必须存在用户根对象。
     */
    public UserProfileAggregate {
        Objects.requireNonNull(user, "user must not be null");
    }
}
