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
 * 源文档逻辑身份与当前处理指针持久化实体。
 *
 * <p>当前文件、解析、分块和发布指针相互独立；更新文件或重新分块期间，
 * 当前发布指针可以继续指向旧的不可变发布 Revision。</p>
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_source_document")
public class KbSourceDocumentEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档逻辑业务标识。 */
    private String documentKey;

    /** 所属知识库数据库主键。 */
    private Long knowledgeBaseId;

    /** 当前文件的展示名称。 */
    private String displayName;

    /** 当前不可变文件版本数据库主键。 */
    private Long currentDocumentVersionId;

    /** 当前解析 Revision 数据库主键。 */
    private Long currentParseRevisionId;

    /** 当前解析侧分块 Revision 数据库主键。 */
    private Long currentChunkRevisionId;

    /** 当前生效发布 Revision 数据库主键。 */
    private Long activePublicationRevisionId;

    /** 当前解析状态。 */
    private String parseStatus;

    /** 当前发布状态。 */
    private String publishStatus;
}
