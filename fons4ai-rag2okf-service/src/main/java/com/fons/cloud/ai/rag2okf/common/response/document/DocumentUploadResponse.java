package com.fons.cloud.ai.rag2okf.common.response.document;

/**
 * 上传新文档受理响应契约（设计 §3.2 上传契约）。
 *
 * <p>只返回受理事实与后续跟踪入口：不包含数据库 id、objectKey、凭证
 * 或历史文件列表。仅保存或知识库默认不解析时 {@code taskKey} 为空。</p>
 *
 * @param documentKey      文档业务标识
 * @param currentFileToken 当前源文件 CAS 令牌
 * @param displayName      文档展示名称
 * @param processingMode   本次生效的处理模式
 * @param taskKey          受理的 PARSE 任务标识，未创建任务时为 null
 * @author hongqy
 */
public record DocumentUploadResponse(
        String documentKey,
        String currentFileToken,
        String displayName,
        String processingMode,
        String taskKey) {
}
