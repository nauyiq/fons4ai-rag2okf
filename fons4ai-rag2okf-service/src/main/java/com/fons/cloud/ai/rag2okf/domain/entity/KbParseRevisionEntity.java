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
 * 不可变解析结果及执行轨迹持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_parse_revision")
public class KbParseRevisionEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 解析 Revision 业务标识。 */
    private String parseRevisionKey;

    /** 源文档数据库主键。 */
    private Long sourceDocumentId;

    /** 被解析的不可变文件版本数据库主键。 */
    private Long documentVersionId;

    /** 解析器 Profile 输入 JSON 快照。 */
    private String parserProfileJson;

    /** 解析器选择和执行轨迹 JSON。 */
    private String parserTraceJson;

    /** 解析结果 manifest 对象 key。 */
    private String manifestObjectKey;

    /** 来源锚点 manifest 对象 key。 */
    private String anchorManifestObjectKey;

    /** 解析块数量。 */
    private Integer blockCount;

    /** 解析 Revision 状态。 */
    private String status;

    /** 安全化错误码。 */
    private String errorCode;

    /** 安全化错误摘要，不保存正文或凭证。 */
    private String errorMessage;
}
