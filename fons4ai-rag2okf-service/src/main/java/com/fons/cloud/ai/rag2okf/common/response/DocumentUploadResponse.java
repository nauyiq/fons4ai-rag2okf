package com.fons.cloud.ai.rag2okf.common.response;

/**
 * 文档上传或更新文件后的受理响应。
 *
 * <p>不返回版本序号、版本列表或回退操作（D-004）。</p>
 *
 * @param documentKey 文档业务标识
 * @param displayName 文档展示名称
 * @param currentFile 当前文件摘要
 * @param taskKey 异步任务业务标识，无任务时为 null
 * @param parseStatus 当前解析状态
 * @param publishStatus 当前发布状态
 * @author hongqy
 */
public record DocumentUploadResponse(
        String documentKey,
        String displayName,
        CurrentFileSummary currentFile,
        String currentFileToken,
        String taskKey,
        String parseStatus,
        String publishStatus
) {

    /**
     * 当前文件摘要，不包含版本 key 和对象 key。
     *
     * @param filename 文件名
     * @param contentType MIME 类型
     * @param size 文件字节数
     */
    public record CurrentFileSummary(String filename, String contentType, long size) {
    }
}
