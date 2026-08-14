package com.fons.cloud.ai.rag2okf.domain.service.user;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;

/**
 * 知识工作空间领域服务。
 *
 * @author hongqy
 */
public interface KbWorkspaceDomainService extends IService<KbWorkspace> {

     /**
      * 根据用户和工作空间KEY查询用户工作空间聚合对象
      * @param userId        用户ID
      * @param workspaceKey 工作空间KEY
      * @return 用户工作空间聚合对象
      */
     UserWorkspaceAggregate findUserWorkspaceAggregate(Long userId, String workspaceKey);

     /**
      * 根据用户和工作空间ID查询用户工作空间聚合对象
      * @param userId        用户ID
      * @param workspaceId 工作空间ID
      * @return 用户工作空间聚合对象
      */
     UserWorkspaceAggregate findUserWorkspaceAggregate(Long userId, Long workspaceId);

     /**
      * 根据工作空间KEY查询工作空间
      * @param workspaceKey 工作空间KEY
      * @return 工作空间
      */
     KbWorkspace findByWorkspaceKey(String workspaceKey);

}
