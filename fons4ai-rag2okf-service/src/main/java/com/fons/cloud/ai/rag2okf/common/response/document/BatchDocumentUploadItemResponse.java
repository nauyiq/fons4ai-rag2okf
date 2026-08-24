package com.fons.cloud.ai.rag2okf.common.response.document;

/**
 * 批量上传单项结果契约（AC-002）。
 *
 * <p>批量上传每项独立成功/失败：成功项携带受理响应，失败项只携带安全化
 * 错误码与摘要，不透出内部堆栈或存储细节。</p>
 *
 * @param index         批内序号，从 0 开始
 * @param filename      该项上传文件名
 * @param success       该项是否成功
 * @param document      成功时的受理响应，失败时为 null
 * @param errorCode     失败时的稳定错误码，成功时为 null
 * @param errorMessage  失败时的安全化错误摘要，成功时为 null
 * @author hongqy
 */
public record BatchDocumentUploadItemResponse(
        int index,
        String filename,
        boolean success,
        DocumentUploadResponse document,
        String errorCode,
        String errorMessage) {

    /**
     * 构造成功项。
     *
     * @param index    批内序号
     * @param filename 上传文件名
     * @param document 受理响应
     * @return 成功项结果
     */
    public static BatchDocumentUploadItemResponse success(int index, String filename, DocumentUploadResponse document) {
        return new BatchDocumentUploadItemResponse(index, filename, true, document, null, null);
    }

    /**
     * 构造失败项。
     *
     * @param index        批内序号
     * @param filename     上传文件名
     * @param errorCode    稳定错误码
     * @param errorMessage 安全化错误摘要
     * @return 失败项结果
     */
    public static BatchDocumentUploadItemResponse failure(int index, String filename, String errorCode, String errorMessage) {
        return new BatchDocumentUploadItemResponse(index, filename, false, null, errorCode, errorMessage);
    }
}
