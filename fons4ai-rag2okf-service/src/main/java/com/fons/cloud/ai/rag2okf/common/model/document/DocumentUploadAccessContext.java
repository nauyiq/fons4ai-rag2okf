package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbKnowledgeBase;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbUser;
import com.fons.cloud.ai.rag2okf.domain.entity.user.KbWorkspace;
import com.fons.cloud.ai.rag2okf.domain.entity.user.UserWorkspaceAggregate;
import lombok.Getter;

/**
 * 文档上传用例已完成授权后的访问上下文。
 *
 * <p>该对象仅传递应用层编排所需事实，不是领域聚合，也不承担跨领域状态管理。</p>
 */
@Getter
public class DocumentUploadAccessContext {
    /** 发起上传的当前用户。 */
    private final KbUser user;

    /** 本次上传的目标知识库。 */
    private final KbKnowledgeBase knowledgeBase;

    /** 已验证的用户—工作空间聚合。 */
    private final UserWorkspaceAggregate userWorkspace;

    /**
     * 创建已完成访问校验的上传上下文。
     *
     * @param user          当前用户
     * @param knowledgeBase 目标知识库
     * @param userWorkspace 已验证的用户—工作空间聚合
     */
    public DocumentUploadAccessContext(KbUser user, KbKnowledgeBase knowledgeBase, UserWorkspaceAggregate userWorkspace) {
        this.user = user;
        this.knowledgeBase = knowledgeBase;
        this.userWorkspace = userWorkspace;
    }

    /**
     * 获取知识库所属工作空间。
     *
     * @return 已完成成员关系校验的工作空间
     */
    public KbWorkspace getWorkspace() {
        return userWorkspace.getWorkspace();
    }
}
