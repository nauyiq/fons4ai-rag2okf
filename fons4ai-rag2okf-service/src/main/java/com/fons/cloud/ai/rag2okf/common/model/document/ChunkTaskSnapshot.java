package com.fons.cloud.ai.rag2okf.common.model.document;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CHUNK 与 RECHUNK 任务共用的最小冻结输入，不保存模型凭据或对象存储路径。
 *
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChunkTaskSnapshot {

    /** 所属工作空间业务键。 */
    private String workspaceKey;
    /** 所属知识库业务键。 */
    private String knowledgeBaseKey;
    /** 目标文档业务键。 */
    private String documentKey;
    /** 当前结果业务键，执行时必须仍为文档当前结果。 */
    private String resultKey;
    /** 创建任务时持有的结果版本，执行时作为 CAS 前提。 */
    private Integer expectedResultVersion;
    /** 冻结的两维分块策略。 */
    private ChunkPolicy chunkPolicy;
    /** 按能力名称索引的安全模型档案引用。 */
    private Map<String, ModelProfileReference> modelProfileRefs = new LinkedHashMap<>();
    /** 发起用户数据库主键。 */
    private Long requestedBy;
    /** 快照创建时间。 */
    private Date requestedAt;

    /** @return 创建时间的防御性副本。 */
    public Date requestedAt() {
        return requestedAt == null ? null : new Date(requestedAt.getTime());
    }
}
