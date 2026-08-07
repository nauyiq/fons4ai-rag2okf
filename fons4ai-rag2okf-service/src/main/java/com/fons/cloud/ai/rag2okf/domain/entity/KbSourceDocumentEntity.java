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
 * 源文档逻辑身份、文件元数据与当前处理指针持久化实体。
 *
 * <p>文件元数据直接存储在文档行上，更新文件时覆盖字段并删除旧 MinIO 对象。
 * 当前文件、解析、分块和发布指针相互独立；更新文件期间，
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

    /** 文件夹路径，根级为 /。 */
    private String folderPath;

    /** MinIO 系统对象 key。 */
    private String objectKey;

    /** 上传时原始文件名。 */
    private String originalFilename;

    /** 服务端识别的媒体类型。 */
    private String contentType;

    /** 规范化文件扩展名。 */
    private String fileExtension;

    /** 文件字节数。 */
    private Long sizeBytes;

    /** 文件内容 SHA-256。 */
    private String sha256;

    /** 文件更新 CAS 令牌，每次更新重新生成。 */
    private String fileToken;

    /** 上传用户主键。 */
    private Long uploadActorId;

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
