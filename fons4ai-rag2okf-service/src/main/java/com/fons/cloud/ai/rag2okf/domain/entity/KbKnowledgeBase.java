package com.fons.cloud.ai.rag2okf.domain.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.KnowledgeBaseStatus;
import com.fons.cloud.ai.rag2okf.common.dto.ChunkProfile;
import com.fons.cloud.ai.rag2okf.common.exeception.KnowledgeBaseException;
import com.fons.cloud.ai.rag2okf.common.request.ChunkProfileRequest;
import com.fons.cloud.ai.rag2okf.common.request.CreateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 知识库及其默认处理策略持久化实体。
 *
 * <p>领域规则（autoPublish 依赖 autoParse、ChunkProfile 校验）下沉到实体工厂方法和
 * {@link #applyUpdate} 中，应用服务只负责编排和持久化协调。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_knowledge_base")
public class KbKnowledgeBase extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库业务标识。 */
    private String knowledgeBaseKey;

    /** 所属工作空间数据库主键。 */
    private Long workspaceId;

    /** 知识库创建者本地用户主键，用于删除鉴权与列表 canDelete 计算。 */
    private Long ownerUserId;

    /** 知识库名称。 */
    private String name;

    /** 知识库说明。 */
    private String description;

    /** 上传后是否自动解析。 */
    private Boolean autoParse;

    /** 解析成功后是否自动发布。 */
    private Boolean autoPublish;

    /** 默认解析器 Profile 标识。 */
    private String parserProfile;

    /** 默认分块策略 JSON 快照。 */
    private String chunkProfileJson;

    /** 知识库状态。 */
    private KnowledgeBaseStatus status;

    /**
     * 创建知识库实体。autoPublish 为 true 时 autoParse 必须为 true，否则抛出领域异常。
     *
     * @param ownerUserId 创建者本地用户主键
     * @param workspaceId 所属工作空间主键
     * @param request 创建请求
     * @return 已初始化的知识库实体
     */
    public static KbKnowledgeBase create(Long ownerUserId, Long workspaceId, CreateKnowledgeBaseRequest request) {
        validateAutoPublishDependency(request.autoParse(), request.autoPublish());
        KbKnowledgeBase kbKnowledgeBase = new KbKnowledgeBase();
        kbKnowledgeBase.setKnowledgeBaseKey(BusinessKeyGenerator.nextKey());
        kbKnowledgeBase.setWorkspaceId(workspaceId);
        kbKnowledgeBase.setOwnerUserId(ownerUserId);
        kbKnowledgeBase.setName(request.name());
        kbKnowledgeBase.setDescription(request.description());
        kbKnowledgeBase.setAutoParse(request.autoParse());
        kbKnowledgeBase.setAutoPublish(request.autoPublish());
        kbKnowledgeBase.setParserProfile(request.parserProfile());
        ChunkProfileRequest chunkProfileRequest = request.chunkProfile();
        kbKnowledgeBase.setChunkProfileJson(JSON.toJSONString(new ChunkProfile(chunkProfileRequest)));
        kbKnowledgeBase.setStatus(KnowledgeBaseStatus.ACTIVE);
        return kbKnowledgeBase;
    }

    /**
     * 按更新请求合并可变字段。null 字段表示不修改。
     *
     * <p>领域校验（ChunkProfile 构造、autoPublish 依赖 autoParse）在此方法内完成，
     * 校验失败时抛出 {@link KnowledgeBaseException}，由全局异常处理器收敛为 400。</p>
     *
     * @param request 更新请求，字段 null 表示不修改
     */
    public void applyUpdate(UpdateKnowledgeBaseRequest request) {
        if (request.name() != null) {
            this.name = request.name().trim();
        }
        if (request.description() != null) {
            String trimmed = request.description().trim();
            this.description = trimmed.isEmpty() ? null : trimmed;
        }
        if (request.autoParse() != null) {
            this.autoParse = request.autoParse();
        }
        if (request.autoPublish() != null) {
            this.autoPublish = request.autoPublish();
        }
        if (request.parserProfile() != null) {
            this.parserProfile = request.parserProfile().trim();
        }
        if (request.chunkProfile() != null) {
            this.chunkProfileJson = JSON.toJSONString(new ChunkProfile(request.chunkProfile()));
        }
        validateAutoPublishDependency(this.autoParse, this.autoPublish);
    }

    /**
     * 校验 autoPublish 依赖 autoParse 的跨字段规则。
     *
     * @param autoParse 是否自动解析
     * @param autoPublish 是否自动发布
     * @throws KnowledgeBaseException 当 autoPublish 为 true 但 autoParse 不为 true 时
     */
    private static void validateAutoPublishDependency(Boolean autoParse, Boolean autoPublish) {
        if (Boolean.TRUE.equals(autoPublish) && !Boolean.TRUE.equals(autoParse)) {
            throw new KnowledgeBaseException();
        }
    }


}
