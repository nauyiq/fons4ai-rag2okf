package com.fons.cloud.ai.rag2okf.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbModelProfile;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户模型档案领域服务。
 *
 * @author hongqy
 */
public interface KbModelProfileDomainService extends IService<KbModelProfile> {

    /**
     * 批量查询指定所有者名下的有效模型档案。
     *
     * @param profileKeys 模型档案业务标识集合
     * @param ownerUserId 档案所有者用户主键
     * @return 属于指定所有者且未删除的模型档案
     */
    List<KbModelProfile> listByProfileKeysAndOwnerUserId(Set<String> profileKeys, Long ownerUserId);

    /**
     * 批量查询模型档案主键对应的业务标识。
     *
     * @param profileIds 模型档案主键集合
     * @return 模型档案主键到业务标识的映射
     */
    Map<Long, String> findProfileKeysByIds(Set<Long> profileIds);

    /**
     * 查询指定用户拥有的全部有效模型档案，并按更新时间倒序返回。
     *
     * @param ownerUserId 档案所有者用户主键
     * @return 用户拥有的模型档案，不包含已删除数据
     */
    List<KbModelProfile> listByOwnerUserId(Long ownerUserId);

    /**
     * 查询指定用户在目标连接下拥有的有效模型档案，并按更新时间倒序返回。
     *
     * @param ownerUserId 档案所有者用户主键
     * @param connectionId Provider 连接主键
     * @return 目标连接下属于该用户的模型档案，不包含已删除数据
     */
    List<KbModelProfile> listByOwnerUserIdAndConnectionId(Long ownerUserId, Long connectionId);

    /**
     * 按业务标识和所有者查询有效模型档案。
     *
     * @param profileKey 模型档案业务标识
     * @param ownerUserId 档案所有者用户主键
     * @return 匹配的模型档案；不存在、已删除或不属于该用户时返回 {@code null}
     */
    KbModelProfile findByProfileKeyAndOwnerUserId(String profileKey, Long ownerUserId);

    /**
     * 软删除指定用户在目标连接下拥有的全部有效模型档案。
     *
     * @param connectionId Provider 连接主键
     * @param ownerUserId 档案所有者用户主键
     * @return 是否成功执行删除
     */
    boolean removeByConnectionIdAndOwnerUserId(Long connectionId, Long ownerUserId);

}
