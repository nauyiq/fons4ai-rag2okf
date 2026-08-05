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
 * 知识库及其默认处理策略持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_knowledge_base")
public class KbKnowledgeBaseEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 知识库业务标识。 */
    private String knowledgeBaseKey;

    /** 所属工作空间数据库主键。 */
    private Long workspaceId;

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
    private String status;
}
