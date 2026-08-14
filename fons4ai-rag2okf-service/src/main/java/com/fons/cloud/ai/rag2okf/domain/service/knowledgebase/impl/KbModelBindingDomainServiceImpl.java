package com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelBindingStatus;
import com.fons.cloud.ai.rag2okf.common.constants.knowledgebase.ModelUsageType;
import com.fons.cloud.ai.rag2okf.domain.entity.knowledgebase.KbModelBinding;
import com.fons.cloud.ai.rag2okf.domain.mapper.knowledgebase.KbModelBindingMapper;
import com.fons.cloud.ai.rag2okf.domain.service.knowledgebase.KbModelBindingDomainService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库模型用途绑定领域服务实现。
 *
 * @author hongqy
 */
@Service
public class KbModelBindingDomainServiceImpl
        extends ServiceImpl<KbModelBindingMapper, KbModelBinding>
        implements KbModelBindingDomainService {

    @Override
    public List<KbModelBinding> listByKnowledgeBaseId(Long knowledgeBaseId) {
        return list(Wrappers.<KbModelBinding>lambdaQuery()
                .eq(KbModelBinding::getKnowledgeBaseId, knowledgeBaseId)
                .eq(KbModelBinding::getStatus, ModelBindingStatus.ACTIVE));
    }

    @Override
    public List<KbModelBinding> replaceByKnowledgeBaseId(
            Long knowledgeBaseId, List<KbModelBinding> requestedBindings) {
        List<KbModelBinding> existingBindings = list(Wrappers.<KbModelBinding>lambdaQuery()
                .eq(KbModelBinding::getKnowledgeBaseId, knowledgeBaseId));
        Map<ModelUsageType, KbModelBinding> existingByUsage = new LinkedHashMap<>();
        for (KbModelBinding existing : existingBindings) {
            existingByUsage.put(existing.getUsageType(), existing);
        }

        List<KbModelBinding> updates = new ArrayList<>();
        List<KbModelBinding> inserts = new ArrayList<>();
        List<KbModelBinding> activeBindings = new ArrayList<>();
        for (KbModelBinding requested : requestedBindings) {
            KbModelBinding existing = existingByUsage.remove(requested.getUsageType());
            if (existing == null) {
                inserts.add(requested);
                activeBindings.add(requested);
                continue;
            }
            existing.bindToProfile(requested.getModelProfileId());
            updates.add(existing);
            activeBindings.add(existing);
        }
        for (KbModelBinding obsolete : existingByUsage.values()) {
            obsolete.disable();
            updates.add(obsolete);
        }

        if (!updates.isEmpty() && !updateBatchById(updates)) {
            throw new IllegalStateException("更新知识库模型绑定失败");
        }
        if (!inserts.isEmpty() && !saveBatch(inserts)) {
            throw new IllegalStateException("新增知识库模型绑定失败");
        }
        return activeBindings;
    }
}
