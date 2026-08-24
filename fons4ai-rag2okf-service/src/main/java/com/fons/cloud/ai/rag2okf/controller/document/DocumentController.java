package com.fons.cloud.ai.rag2okf.controller.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.application.document.DocumentApplicationService;
import com.fons.cloud.ai.rag2okf.application.document.DocumentApplicationService.DocumentFileContent;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentUploadRequest;
import com.fons.cloud.ai.rag2okf.common.response.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.BatchDocumentUploadItemResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentUploadResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档上传、批量上传、列表、详情与源文件下载的 HTTP 接口（TP-002 T011 新契约）。
 *
 * <p>Controller 只承担 HTTP 入参/出参转换，不承载业务规则。上传契约从旧
 * {@code parseMode/folderPath} 整体切换为 {@code processingMode/parserType/chunkPolicy}
 * 加 {@code Idempotency-Key} 头；非法枚举与 JSON 解析失败以
 * {@code PAYLOAD_INVALID} 拒绝，不做默认回退。本期不支持替换源文件，
 * 不提供 {@code POST /documents/{documentKey}/files} 入口（设计 §3.2）。</p>
 *
 * <h3>过渡说明</h3>
 * <p>列表/详情/下载沿用旧响应结构（T012 按新契约重写后移除）；上传响应只含
 * documentKey/currentFileToken/displayName/processingMode/taskKey，
 * 不返回数据库 id、objectKey、凭证或历史文件列表。</p>
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;

    /**
     * 上传新文档：每个合法文件都成为独立文档，同名不合并（AC-001）。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param file             上传文件
     * @param processingMode   处理模式：DEFAULT、PARSE 或 SKIP，缺省 DEFAULT
     * @param parserType       解析器类型，缺省 BUILT_IN；MINERU 返回 PARSER_NOT_AVAILABLE
     * @param chunkPolicy      分块策略 JSON，缺省知识库默认；非法值拒绝不回退
     * @param idempotencyKey   调用方幂等键，相同 key 重放返回原结果
     * @return 上传受理响应
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseKey}/documents")
    public R<DocumentUploadResponse> uploadDocument(
            @PathVariable String knowledgeBaseKey,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "processingMode", required = false) String processingMode,
            @RequestPart(value = "parserType", required = false) String parserType,
            @RequestPart(value = "chunkPolicy", required = false) String chunkPolicy,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(documentApplicationService.uploadDocument(
                knowledgeBaseKey, file,
                toUploadRequest(processingMode, parserType, chunkPolicy),
                idempotencyKey));
    }

    /**
     * 批量上传文档：每项独立成功/失败，部分失败不回滚已成功项（AC-002）。
     *
     * <p>批量幂等以 request key + item index 区分；单批上限 50 文件且总计 500 MiB，
     * 超限整批预检拒绝。</p>
     *
     * @param knowledgeBaseKey 知识库标识
     * @param files            上传文件列表
     * @param processingMode   处理模式，对批内每项一致生效
     * @param parserType       解析器类型，对批内每项一致生效
     * @param chunkPolicy      分块策略 JSON，对批内每项一致生效
     * @param idempotencyKey   批量请求幂等键
     * @return 每项独立成功/失败结果，顺序与入参一致
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseKey}/documents/batch")
    public R<List<BatchDocumentUploadItemResponse>> batchUploadDocuments(
            @PathVariable String knowledgeBaseKey,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart(value = "processingMode", required = false) String processingMode,
            @RequestPart(value = "parserType", required = false) String parserType,
            @RequestPart(value = "chunkPolicy", required = false) String chunkPolicy,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(documentApplicationService.batchUploadDocuments(
                knowledgeBaseKey, files,
                toUploadRequest(processingMode, parserType, chunkPolicy),
                idempotencyKey));
    }

    // ────────────────────────────── 过渡读入口（T012 重写后移除） ──────────────────────────────

    /**
     * 查询知识库下的文档当前视图（旧读链路过渡实现）。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param page             页码，从 0 开始
     * @param size             每页条数
     * @param folderPath       文件夹路径筛选，不传时不过滤
     * @return 文档分页列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseKey}/documents")
    public R<PageResponse<DocumentSummaryResponse>> listDocuments(
            @PathVariable String knowledgeBaseKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(value = "folderPath", required = false) String folderPath) {
        return R.ok(documentApplicationService.listDocuments(knowledgeBaseKey, page, size, folderPath));
    }

    /**
     * 查询文档详情（旧读链路过渡实现）。
     *
     * @param documentKey 文档标识
     * @return 文档详情响应
     */
    @GetMapping("/documents/{documentKey}")
    public R<DocumentDetailResponse> getDocumentDetail(@PathVariable String documentKey) {
        return R.ok(documentApplicationService.getDocumentDetail(documentKey));
    }

    /**
     * 下载文档当前源文件（旧读链路过渡实现）。
     *
     * @param documentKey 文档标识
     * @return 文件流响应
     */
    @GetMapping("/documents/{documentKey}/file")
    public ResponseEntity<InputStreamResource> downloadDocumentFile(@PathVariable String documentKey) {
        DocumentFileContent content = documentApplicationService.downloadDocumentFile(documentKey);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.size())
                .body(new InputStreamResource(content.inputStream()));
    }

    // ────────────────────────────── 入参转换 ──────────────────────────────

    /**
     * 把 multipart 表单参数组装为上传请求契约。
     *
     * <p>枚举严格解析：未知 processingMode/parserType 直接 {@code PAYLOAD_INVALID}，
     * 不做默认回退；chunkPolicy JSON 解析失败同样拒绝。</p>
     *
     * @param processingMode 处理模式表单值，可空
     * @param parserType     解析器类型表单值，可空
     * @param chunkPolicy    分块策略 JSON 表单值，可空
     * @return 上传请求契约
     */
    private DocumentUploadRequest toUploadRequest(
            String processingMode, String parserType, String chunkPolicy) {
        ProcessingMode mode = ProcessingMode.fromValue(
                processingMode != null ? processingMode : ProcessingMode.DEFAULT.getValue());
        ParserType type = ParserType.fromValue(
                parserType != null ? parserType : ParserType.BUILT_IN.getValue());
        return new DocumentUploadRequest(mode, type, parseChunkPolicy(chunkPolicy));
    }

    /**
     * 解析分块策略 JSON 表单值。
     *
     * @param chunkPolicy 分块策略 JSON，空值返回 {@code null} 表示使用知识库默认
     * @return 分块策略请求对象
     * @throws DocumentProcessingException JSON 结构非法时抛出 {@code PAYLOAD_INVALID}
     */
    private DocumentUploadRequest.ChunkPolicy parseChunkPolicy(String chunkPolicy) {
        if (chunkPolicy == null || chunkPolicy.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(chunkPolicy, DocumentUploadRequest.ChunkPolicy.class);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID, exception);
        }
    }
}
