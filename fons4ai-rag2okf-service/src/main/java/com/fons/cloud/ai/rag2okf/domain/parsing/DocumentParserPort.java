package com.fons.cloud.ai.rag2okf.domain.parsing;

import com.fons.cloud.ai.rag2okf.domain.artifact.DocumentArtifactStore;

/**
 * 文档解析端口，由基础设施适配器实现。
 *
 * <p>适配器负责：
 * <ol>
 *   <li>从 MinIO 读取文档版本原文件</li>
 *   <li>委托 Fons4AI 解析器 SPI 解析</li>
 *   <li>规范化为 ParseManifest 和 SourceAnchor Manifest</li>
 *   <li>写入 MinIO 并返回结果</li>
 * </ol>
 *
 * <p>不伪造页码（AC-014），不静默切换 provider（AC-014）。
 *
 * @author hongqy
 */
public interface DocumentParserPort {

    /**
     * 解析文档版本原文件。
     *
     * @param request 解析请求
     * @return 解析结果，包含 Manifest 和 MinIO 存储信息
     */
    ParseResult parse(ParseRequest request);

    /**
     * 解析请求。
     *
     * @param scope             MinIO 对象范围
     * @param documentVersionKey 文档版本业务标识
     * @param parseRevisionKey  解析 revision 业务标识（用于 MinIO 路径）
     * @param originalFilename  原始文件名（用于推断扩展名和文档类型）
     * @param contentType       已验证的 MIME 类型
     * @param parserProfile     解析器 Profile（NATIVE_TIKA、MINERU_LAYOUT）
     */
    record ParseRequest(
            DocumentArtifactStore.ArtifactScope scope,
            String documentVersionKey,
            String parseRevisionKey,
            String originalFilename,
            String contentType,
            String parserProfile
    ) {
    }

    /**
     * 解析结果。
     *
     * @param manifest             解析 Manifest
     * @param manifestArtifact     ParseManifest 在 MinIO 的存储信息
     * @param sourceAnchorArtifact SourceAnchor Manifest 在 MinIO 的存储信息
     */
    record ParseResult(
            ParseManifest manifest,
            DocumentArtifactStore.StoredArtifact manifestArtifact,
            DocumentArtifactStore.StoredArtifact sourceAnchorArtifact
    ) {
    }
}
