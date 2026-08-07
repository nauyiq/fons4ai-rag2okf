package com.fons.cloud.ai.rag2okf.infrastructure.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.ParseTaskPayload;
import com.fons.cloud.ai.rag2okf.application.task.TaskApplicationService;
import com.fons.cloud.ai.rag2okf.common.exeception.DocumentArtifactException;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentArtifactStore.ArtifactScope;
import com.fons.cloud.ai.rag2okf.domain.entity.KbChunkRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbParseRevisionEntity;
import com.fons.cloud.ai.rag2okf.domain.entity.KbSourceDocumentEntity;
import com.fons.cloud.ai.rag2okf.common.dto.ChunkManifest;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentChunkerPort;
import com.fons.cloud.ai.rag2okf.common.dto.DocumentParserPort;
import com.fons.cloud.ai.rag2okf.common.dto.ParseManifest;
import com.fons.cloud.ai.rag2okf.domain.service.KbChunkRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbParseRevisionDomainService;
import com.fons.cloud.ai.rag2okf.domain.service.KbSourceDocumentDomainService;
import com.fons.cloud.ai.rag2okf.common.dto.ProcessingTask;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionPort;
import com.fons.cloud.ai.rag2okf.common.dto.TaskExecutionResult;
import com.fons.cloud.ai.rag2okf.common.dto.TaskType;
import com.fons.cloud.ai.rag2okf.common.dto.ModelBusinessKeyGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 解析任务执行器，实现 {@link TaskExecutionPort}。
 *
 * <p>执行流程（技术设计 §5.4）：
 * <ol>
 *   <li>反序列化 payloadJson 获取输入快照</li>
 *   <li>调用 {@link DocumentParserPort} 解析文件，生成 ParseManifest</li>
 *   <li>调用 {@link DocumentChunkerPort} 分块，生成 ChunkManifest</li>
 *   <li>事务内创建 ParseRevision、ChunkRevision，切换当前指针</li>
 *   <li>若 autoPublish，创建发布任务（T015 实现后启用）</li>
 * </ol>
 *
 * <p>MinIO 写入在事务外，DB 指针切换在事务内；失败时 MinIO 产物可能孤立但不影响一致性。
 *
 * @author hongqy
 */
@Slf4j
@Component
public class ParseTaskExecutor implements TaskExecutionPort {

    private final DocumentParserPort documentParserPort;
    private final DocumentChunkerPort documentChunkerPort;
    private final KbParseRevisionDomainService parseRevisionDomainService;
    private final KbChunkRevisionDomainService chunkRevisionDomainService;
    private final KbSourceDocumentDomainService sourceDocumentDomainService;
    private final TaskApplicationService taskApplicationService;
    private final ModelBusinessKeyGenerator keyGenerator;
    private final ObjectMapper objectMapper;

    @Lazy
    @Autowired
    private ParseTaskExecutor self;

    public ParseTaskExecutor(DocumentParserPort documentParserPort,
                             DocumentChunkerPort documentChunkerPort,
                             KbParseRevisionDomainService parseRevisionDomainService,
                             KbChunkRevisionDomainService chunkRevisionDomainService,
                             KbSourceDocumentDomainService sourceDocumentDomainService,
                             TaskApplicationService taskApplicationService,
                             ModelBusinessKeyGenerator keyGenerator,
                             ObjectMapper objectMapper) {
        this.documentParserPort = documentParserPort;
        this.documentChunkerPort = documentChunkerPort;
        this.parseRevisionDomainService = parseRevisionDomainService;
        this.chunkRevisionDomainService = chunkRevisionDomainService;
        this.sourceDocumentDomainService = sourceDocumentDomainService;
        this.taskApplicationService = taskApplicationService;
        this.keyGenerator = keyGenerator;
        this.objectMapper = objectMapper;
    }

    @Override
    public TaskType supportedType() {
        return TaskType.PARSE;
    }

    @Override
    public TaskExecutionResult execute(ProcessingTask task) {
        ParseTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.entity().getPayloadJson(), ParseTaskPayload.class);
        } catch (Exception e) {
            log.error("Failed to deserialize parse payload: taskKey={}", task.taskKey(), e);
            return new TaskExecutionResult.FatalFailure(
                    "PAYLOAD_INVALID", "任务输入快照解析失败");
        }

        ArtifactScope scope = new ArtifactScope(
                payload.workspaceKey(), payload.knowledgeBaseKey(), payload.documentKey());
        String parseRevisionKey = keyGenerator.nextKey();
        String chunkRevisionKey = keyGenerator.nextKey();

        try {
            // 1. 解析文件
            log.info("Parse started: taskKey={}, fileToken={}, parserProfile={}",
                    task.taskKey(), payload.fileToken(), payload.parserProfile());

            DocumentParserPort.ParseRequest parseRequest = new DocumentParserPort.ParseRequest(
                    scope, payload.documentKey(), parseRevisionKey,
                    payload.originalFilename(), payload.contentType(), payload.parserProfile());
            DocumentParserPort.ParseResult parseResult = documentParserPort.parse(parseRequest);

            // 2. 分块
            log.info("Chunk started: taskKey={}, parseRevisionKey={}", task.taskKey(), parseRevisionKey);

            DocumentChunkerPort.ChunkRequest chunkRequest = new DocumentChunkerPort.ChunkRequest(
                    scope, parseRevisionKey, chunkRevisionKey, payload.chunkProfile());
            DocumentChunkerPort.ChunkResult chunkResult = documentChunkerPort.chunk(chunkRequest);

            // 3. 事务内创建 Revision 并切换指针
            self.persistParseResult(payload, parseRevisionKey, chunkRevisionKey,
                    parseResult, chunkResult);

            log.info("Parse succeeded: taskKey={}, parseRevisionKey={}, chunkRevisionKey={}, blocks={}, chunks={}",
                    task.taskKey(), parseRevisionKey, chunkRevisionKey,
                    parseResult.manifest().blockCount(), chunkResult.manifest().childCount());

            // 4. 自动发布（T015 实现后启用）
            if (payload.autoPublish()) {
                log.info("Auto-publish scheduled but PUBLISH executor not yet registered (T015): taskKey={}",
                        task.taskKey());
            }

            return new TaskExecutionResult.Succeeded(parseRevisionKey);

        } catch (DocumentArtifactException e) {
            log.warn("Parse failed (document artifact): taskKey={}, message={}",
                    task.taskKey(), e.getMessage());
            return new TaskExecutionResult.RetryableFailure(
                    "PARSE_ARTIFACT_ERROR", safeMessage(e.getMessage()));
        } catch (Exception e) {
            log.error("Parse failed (unexpected): taskKey={}", task.taskKey(), e);
            return new TaskExecutionResult.RetryableFailure(
                    "PARSE_UNEXPECTED_ERROR", "解析执行异常: " + e.getClass().getSimpleName());
        }
    }

    /**
     * 事务内创建 ParseRevision、ChunkRevision，CAS 切换文档指针。
     *
     * <p>通过 @Lazy 自引用触发 Spring AOP 代理，使 @Transactional 生效。
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void persistParseResult(ParseTaskPayload payload,
                                   String parseRevisionKey, String chunkRevisionKey,
                                   DocumentParserPort.ParseResult parseResult,
                                   DocumentChunkerPort.ChunkResult chunkResult) {
        // 创建 ParseRevision
        KbParseRevisionEntity parseRevision = new KbParseRevisionEntity();
        parseRevision.setParseRevisionKey(parseRevisionKey);
        parseRevision.setSourceDocumentId(payload.sourceDocumentId());
        // parserProfile 是字符串枚举值，MySQL JSON 列要求合法 JSON 文本，包装为 JSON 对象
        try {
            parseRevision.setParserProfileJson(
                    objectMapper.writeValueAsString(java.util.Map.of("profile", payload.parserProfile())));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            parseRevision.setParserProfileJson("{\"profile\":null}");
        }
        try {
            parseRevision.setParserTraceJson(
                    objectMapper.writeValueAsString(parseResult.manifest().parserTrace()));
        } catch (Exception e) {
            parseRevision.setParserTraceJson(null);
        }
        parseRevision.setManifestObjectKey(parseResult.manifestArtifact().objectKey());
        parseRevision.setAnchorManifestObjectKey(parseResult.sourceAnchorArtifact().objectKey());
        parseRevision.setBlockCount(parseResult.manifest().blockCount());
        parseRevision.setStatus("SUCCEEDED");
        parseRevisionDomainService.save(parseRevision);

        // 创建 ChunkRevision
        KbChunkRevisionEntity chunkRevision = new KbChunkRevisionEntity();
        chunkRevision.setChunkRevisionKey(chunkRevisionKey);
        chunkRevision.setSourceDocumentId(payload.sourceDocumentId());
        chunkRevision.setParseRevisionId(parseRevision.getId());
        try {
            chunkRevision.setChunkProfileJson(
                    objectMapper.writeValueAsString(payload.chunkProfile()));
        } catch (Exception e) {
            chunkRevision.setChunkProfileJson(null);
        }
        chunkRevision.setManifestObjectKey(chunkResult.manifestArtifact().objectKey());
        chunkRevision.setParentCount(chunkResult.manifest().parentCount());
        chunkRevision.setChildCount(chunkResult.manifest().childCount());
        chunkRevision.setContentHash(chunkResult.manifest().contentHash());
        chunkRevision.setStatus("SUCCEEDED");
        chunkRevisionDomainService.save(chunkRevision);

        // CAS 切换文档指针
        KbSourceDocumentEntity document = sourceDocumentDomainService.getById(payload.sourceDocumentId());
        if (document == null) {
            throw new DocumentArtifactException("文档不存在: " + payload.documentKey());
        }
        document.setCurrentParseRevisionId(parseRevision.getId());
        document.setCurrentChunkRevisionId(chunkRevision.getId());
        document.setParseStatus("SUCCEEDED");
        boolean updated = sourceDocumentDomainService.updateById(document);
        if (!updated) {
            throw new DocumentArtifactException("文档指针切换失败（CAS 冲突）: " + payload.documentKey());
        }
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "解析产物处理失败";
        }
        return message;
    }
}
