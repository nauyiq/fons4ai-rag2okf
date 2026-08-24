package com.fons.cloud.ai.rag2okf.domain.entity.document;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.CleanupStatus;
import com.fons.cloud.ai.rag2okf.common.constants.document.DocumentStatus;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.utils.BusinessKeyGenerator;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 文档身份、用户可见状态与删除审计持久化实体。
 *
 * <p>承载文档身份不变量与用户可见状态机（设计 §4.5）：同名文档各占独立
 * {@code documentKey} 天然并存，状态只允许白名单流转，软删除同时置清理状态。
 * 文件元数据与制品指针不在本表，统一归 {@link KbDocumentResult}。</p>
 *
 * <p>领域规则下沉到 {@link #create}、{@link #transitionStatus} 与 {@link #softDelete}，
 * 应用服务只负责编排与持久化协调。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document")
public class KbDocument extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档稳定业务标识，同名文档各占独立 key，不作唯一性合并。 */
    private String documentKey;

    /** 所属知识库数据库主键。 */
    private Long knowledgeBaseId;

    /** 文档展示名称，默认取上传文件名，仅展示用，不参与唯一约束。 */
    private String displayName;

    /** 用户可见状态，按 DocumentStatus 白名单流转。 */
    private DocumentStatus status;

    /** 内容清理状态，删除受理后由清理任务驱动。 */
    private CleanupStatus cleanupStatus;

    /** 执行删除的用户主键，仅删除受理后写入。 */
    private Long deletedBy;

    /** 业务删除受理时间，仅删除受理后写入。 */
    private Date deletedAt;

    /**
     * 创建文档实体。
     *
     * <p>每次上传无条件新建文档：documentKey 与 displayName 无唯一性关联，
     * 同名文档各占独立 key 天然并存；初始状态为 {@code UPLOADED}，
     * 清理状态为 {@code NOT_REQUIRED}。</p>
     *
     * @param knowledgeBaseId 所属知识库主键
     * @param displayName     展示名称，默认取上传文件名
     * @return 已初始化的文档实体
     */
    public static KbDocument create(Long knowledgeBaseId, String displayName) {
        KbDocument document = new KbDocument();
        document.setDocumentKey(BusinessKeyGenerator.nextKey());
        document.setKnowledgeBaseId(knowledgeBaseId);
        document.setDisplayName(displayName);
        document.setStatus(DocumentStatus.UPLOADED);
        document.setCleanupStatus(CleanupStatus.NOT_REQUIRED);
        return document;
    }

    /**
     * 按白名单流转用户可见状态。
     *
     * <p>只允许设计 §4.5 状态机内的转移；删除受理必须走 {@link #softDelete}
     * 以携带清理状态与删除审计，同状态重复流转与阶段跳跃均拒绝。</p>
     *
     * @param target 目标状态
     * @throws DocumentProcessingException 非法转移时抛出，语义为当前状态已不是调用方持有的状态
     */
    public void transitionStatus(DocumentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new DocumentProcessingException(Rag2OkfResultCode.TASK_INPUT_SUPERSEDED);
        }
        this.status = target;
    }

    /**
     * 软删除文档。
     *
     * <p>删除幂等：已处于 DELETED 的文档重复删除不产生变化。首次删除受理时
     * 同时置用户可见状态 DELETED、清理状态 PENDING 与逻辑删除标记，并记录
     * 删除审计；外部内容物理清理由 DELETE_CLEANUP 任务异步执行。</p>
     *
     * @param deletedBy 执行删除的用户主键
     */
    public void softDelete(Long deletedBy) {
        if (this.status == DocumentStatus.DELETED) {
            return;
        }
        this.status = DocumentStatus.DELETED;
        this.cleanupStatus = CleanupStatus.PENDING;
        this.setDeleted(Boolean.TRUE);
        this.deletedBy = deletedBy;
        this.deletedAt = new Date();
    }
}
