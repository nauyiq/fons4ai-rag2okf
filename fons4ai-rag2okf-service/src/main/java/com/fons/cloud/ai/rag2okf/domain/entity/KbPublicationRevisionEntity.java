package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.util.Date;

/**
 * 发布快照与 Elasticsearch 投影元数据持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_publication_revision")
public class KbPublicationRevisionEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 发布 Revision 业务标识。 */
    private String publicationRevisionKey;

    /** 源文档数据库主键。 */
    private Long sourceDocumentId;

    /** 发布快照文件版本数据库主键。 */
    private Long documentVersionId;

    /** 发布快照解析 Revision 数据库主键。 */
    private Long parseRevisionId;

    /** 发布快照分块 Revision 数据库主键。 */
    private Long chunkRevisionId;

    /** 发布 manifest 对象 key。 */
    private String manifestObjectKey;

    /** Elasticsearch 物理投影索引。 */
    private String projectionIndex;

    /** 写入 Elasticsearch 的分块数量。 */
    private Integer projectionCount;

    /** 发布状态。 */
    private String status;

    /** 触发方式：MANUAL 或 AUTO。 */
    private String triggerType;

    /** 安全化错误码。 */
    private String errorCode;

    /** 安全化错误摘要，不保存正文或凭证。 */
    private String errorMessage;

    /** 成功发布时间。 */
    private Date publishedAt;
}
