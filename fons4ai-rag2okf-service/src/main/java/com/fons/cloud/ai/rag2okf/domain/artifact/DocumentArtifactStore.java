package com.fons.cloud.ai.rag2okf.domain.artifact;

import java.io.InputStream;
import java.util.Map;

/**
 * 文档原文件和派生产物的私有对象存储端口。
 *
 * @author hongqy
 */
public interface DocumentArtifactStore {

    /**
     * 保存某个不可变文档版本的原文件。
     *
     * @param request 原文件写入请求
     * @return 已保存对象的受控描述信息
     */
    StoredArtifact storeOriginal(OriginalArtifactRequest request);

    /**
     * 保存某个解析、分块或发布 revision 对应的 JSON manifest。
     *
     * @param request manifest 写入请求
     * @return 已保存对象的受控描述信息
     */
    StoredArtifact storeManifest(ManifestArtifactRequest request);

    /**
     * 打开私有对象的读取流；调用方负责关闭该流。
     *
     * @param reference 系统生成的产物定位信息
     * @return 只包含读取流的私有对象内容
     */
    ArtifactContent open(ArtifactReference reference);

    /**
     * 判断指定私有对象是否存在。
     *
     * @param reference 系统生成的产物定位信息
     * @return true 表示对象存在
     */
    boolean exists(ArtifactReference reference);

    /**
     * 删除指定私有对象；仅供显式补偿与生命周期清理使用。
     *
     * @param reference 系统生成的产物定位信息
     */
    void delete(ArtifactReference reference);

    /**
     * 用于生成对象 key 的租户与文档范围，所有值均为系统业务 key。
     *
     * @param workspaceKey 工作空间业务 key
     * @param knowledgeBaseKey 知识库业务 key
     * @param documentKey 源文档业务 key
     */
    record ArtifactScope(String workspaceKey, String knowledgeBaseKey, String documentKey) {
    }

    /**
     * 产物类型决定固定的系统路径，不接受调用方直接传入 object key。
     */
    enum ArtifactType {
        ORIGINAL,
        PARSED_MANIFEST,
        SOURCE_ANCHOR_MANIFEST,
        CHUNK_MANIFEST,
        PUBLICATION_MANIFEST
    }

    /**
     * 原文件写入命令；文件内容以流传递，不能在日志或异常中输出正文。
     *
     * @param scope 原文件所属范围
     * @param versionKey 文档版本业务 key
     * @param originalFilename 已净化的原始文件名，仅作为 metadata
     * @param contentType 已验证的 MIME 类型
     * @param inputStream 原文件读取流
     */
    record OriginalArtifactRequest(
            ArtifactScope scope,
            String versionKey,
            String originalFilename,
            String contentType,
            InputStream inputStream
    ) {
    }

    /**
     * Manifest 写入命令。
     *
     * @param scope manifest 所属范围
     * @param type manifest 类型，不能为 ORIGINAL
     * @param revisionKey 对应 revision 业务 key
     * @param inputStream JSON manifest 流
     */
    record ManifestArtifactRequest(
            ArtifactScope scope,
            ArtifactType type,
            String revisionKey,
            InputStream inputStream
    ) {
    }

    /**
     * 私有对象引用；不包含可公开访问 URL。
     *
     * @param scope 产物所属范围
     * @param type 产物类型
     * @param revisionKey 版本或 revision 业务 key
     */
    record ArtifactReference(ArtifactScope scope, ArtifactType type, String revisionKey) {
    }

    /**
     * 已写入对象的内容摘要与安全 metadata；hash 由服务端流式计算。
     *
     * @param objectKey 系统生成的内部对象 key
     * @param sha256 内容 SHA-256 十六进制摘要
     * @param sizeBytes 实际写入字节数
     * @param metadata 允许保存的对象 metadata
     */
    record StoredArtifact(String objectKey, String sha256, long sizeBytes, Map<String, String> metadata) {

        public StoredArtifact {
            metadata = Map.copyOf(metadata);
        }
    }

    /**
     * 私有对象读取结果。
     *
     * @param inputStream 调用方必须关闭的读取流
     */
    record ArtifactContent(InputStream inputStream) {
    }
}
