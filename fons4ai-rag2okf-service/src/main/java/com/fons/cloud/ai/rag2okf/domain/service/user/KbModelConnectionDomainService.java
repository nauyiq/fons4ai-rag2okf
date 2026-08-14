package com.fons.cloud.ai.rag2okf.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelConnection;

import java.util.List;

/**
 * 用户级 Provider 连接领域服务。
 *
 * @author hongqy
 */
public interface KbModelConnectionDomainService extends IService<KbModelConnection> {

    /**
     * 查询指定用户拥有的 Provider 连接，按最近更新时间倒序返回。
     *
     * @param ownerUserId 连接所有者的用户主键
     * @return 当前用户拥有且未删除的连接列表
     */
    List<KbModelConnection> listByOwnerUserId(Long ownerUserId);

    /**
     * 按业务标识和所有者查询 Provider 连接。
     *
     * @param connectionKey Provider 连接业务标识
     * @param ownerUserId 连接所有者的用户主键
     * @return 匹配的连接；不存在或不属于该用户时返回 {@code null}
     */
    KbModelConnection findByConnectionKeyAndOwnerUserId(String connectionKey, Long ownerUserId);

    /**
     * 按数据库主键和所有者查询 Provider 连接。
     *
     * @param connectionId Provider 连接数据库主键
     * @param ownerUserId 连接所有者的用户主键
     * @return 匹配的连接；不存在或不属于该用户时返回 {@code null}
     */
    KbModelConnection findByIdAndOwnerUserId(Long connectionId, Long ownerUserId);
}
