package com.fons.cloud.ai.rag2okf.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fons.cloud.db.entity.CommonEntity;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;

/**
 * 解析侧可替换分块集合持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_chunk_revision")
public class KbChunkRevisionEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 分块 Revision 业务标识。 */
    private String chunkRevisionKey;

    /** 源文档数据库主键。 */
    private Long sourceDocumentId;

    /** 所属解析 Revision 数据库主键。 */
    private Long parseRevisionId;

    /** 分块策略输入 JSON 快照。 */
    private String chunkProfileJson;

    /** 分块 manifest 对象 key。 */
    private String manifestObjectKey;

    /** 父分块数量。 */
    private Integer parentCount;

    /** 子分块数量。 */
    private Integer childCount;

    /** 规范化分块集合内容摘要。 */
    private String contentHash;

    /** 分块 Revision 状态。 */
    private String status;
}
