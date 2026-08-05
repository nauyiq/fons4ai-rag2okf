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
 * 不可变文档文件版本持久化实体。
 *
 * @author hongqy
 */
@Getter
@Setter
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("kb_document_version")
public class KbDocumentVersionEntity extends CommonEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 不可变文件版本业务标识。 */
    private String versionKey;

    /** 源文档数据库主键。 */
    private Long sourceDocumentId;

    /** MinIO 系统对象 key，不使用原始文件名拼接。 */
    private String objectKey;

    /** 上传时原始文件名，只用于展示和审计。 */
    private String originalFilename;

    /** 服务端识别的媒体类型。 */
    private String contentType;

    /** 规范化文件扩展名。 */
    private String fileExtension;

    /** 文件字节数。 */
    private Long sizeBytes;

    /** 文件内容 SHA-256。 */
    private String sha256;

    /** 上传用户数据库主键。 */
    private Long uploadActorId;
}
