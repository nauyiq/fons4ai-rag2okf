package com.fons.cloud.ai.rag2okf.domain.service.knowledgebase;

import com.baomidou.mybatisplus.extension.service.IService;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbModelBinding;

import java.util.List;

/**
 * 知识库模型用途绑定领域服务。
 *
 * @author hongqy
 */
public interface KbModelBindingDomainService extends IService<KbModelBinding> {

    /**
     * 查询知识库的全部模型用途绑定。
     *
     * @param knowledgeBaseId 知识库主键
     * @return 模型用途绑定列表
     */
    List<KbModelBinding> listByKnowledgeBaseId(Long knowledgeBaseId);

    /**
     * 按用途整体替换知识库模型绑定。
     *
     * <p>已有用途原位更新，未提交用途转为停用，新增用途才插入新记录，
     * 避免与数据库的知识库用途唯一键冲突。</p>
     *
     * @param knowledgeBaseId 知识库主键
     * @param requestedBindings 已完成业务校验的目标绑定列表
     * @return 替换后的启用绑定，顺序与目标绑定列表一致
     */
    List<KbModelBinding> replaceByKnowledgeBaseId(
            Long knowledgeBaseId, List<KbModelBinding> requestedBindings);
}
