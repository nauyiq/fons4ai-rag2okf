package com.fons.cloud.ai.rag2okf.controller.document;

import com.alibaba.fastjson2.JSON;
import com.fons.cloud.ai.rag2okf.application.document.DocumentApplicationService;
import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileContent;
import com.fons.cloud.ai.rag2okf.application.document.DocumentParseApplicationService;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.constants.document.ProcessingMode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentUploadRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.ChunkPolicyRequest;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentParseRequest;
import com.fons.cloud.common.result.PageResult;
import com.fons.cloud.ai.rag2okf.common.response.document.BatchDocumentUploadItemResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentParseResponse;
import com.fons.cloud.common.result.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文档上传、批量上传、列表、详情与源文件下载的 HTTP 接口（TP-002 T011～T012）。
 *
 * <p>Controller 只承担 HTTP 入参/出参转换，不承载业务规则。上传契约从旧
 * {@code parseMode/folderPath} 整体切换为 {@code processingMode/parserType/chunkPolicy}；
 * 非法枚举与 JSON 解析失败以
 * {@code PAYLOAD_INVALID} 拒绝，不做默认回退。本期不支持替换源文件，
 * 不提供 {@code POST /documents/{documentKey}/files} 入口（设计 §3.2）。</p>
 *
 * <p>上传响应只含 documentKey/currentFileToken/displayName/processingMode/taskKey；
 * 读响应使用状态、阶段、当前文件和最近任务安全摘要，均不返回数据库 id、objectKey、
 * 凭证、文件夹路径或历史文件列表。</p>
 *
 * @author hongqy
 */
@RestController
@Validated
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;
    private final DocumentParseApplicationService documentParseApplicationService;

    /**
     * 对文档当前源文件发起手动解析。
     *
     * @param documentKey 文档业务标识
     * @param request 解析器、分块策略和后续发布意图
     * @return 新建的解析任务摘要
     */
    @PostMapping("/documents/{documentKey}/parse")
    public R<DocumentParseResponse> parseDocument(
            @PathVariable @NotBlank String documentKey,
            @RequestBody(required = false) @Valid DocumentParseRequest request) {
        return documentParseApplicationService.startParse(documentKey, request);
    }

    /**
     * 上传新文档：每个合法文件都成为独立文档，同名不合并（AC-001）。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param file             上传文件
     * @param processingMode   处理模式：DEFAULT、PARSE 或 SKIP，缺省 DEFAULT
     * @param parserType       解析器类型，缺省 BUILT_IN；MINERU 返回 PARSER_NOT_AVAILABLE
     * @param chunkPolicy      分块策略 JSON，缺省知识库默认；非法值拒绝不回退
     * @return 上传受理响应
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseKey}/documents")
    public R<DocumentUploadResponse> uploadDocument(
            @PathVariable @NotBlank String knowledgeBaseKey,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "processingMode", required = false) String processingMode,
            @RequestPart(value = "parserType", required = false) String parserType,
            @RequestPart(value = "chunkPolicy", required = false) String chunkPolicy) {
        return documentApplicationService.uploadDocument(
                knowledgeBaseKey, file,
                toUploadRequest(processingMode, parserType, chunkPolicy));
    }

    /**
     * 批量上传文档：每项独立成功/失败，部分失败不回滚已成功项（AC-002）。
     *
     * <p>单批上限 50 文件且总计 500 MiB，
     * 超限整批预检拒绝。</p>
     *
     * @param knowledgeBaseKey 知识库标识
     * @param files            上传文件列表
     * @param processingMode   处理模式，对批内每项一致生效
     * @param parserType       解析器类型，对批内每项一致生效
     * @param chunkPolicy      分块策略 JSON，对批内每项一致生效
     * @return 每项独立成功/失败结果，顺序与入参一致
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseKey}/documents/batch")
    public R<List<BatchDocumentUploadItemResponse>> batchUploadDocuments(
            @PathVariable @NotBlank String knowledgeBaseKey,
            @RequestPart("files") List<MultipartFile> files,
            @RequestPart(value = "processingMode", required = false) String processingMode,
            @RequestPart(value = "parserType", required = false) String parserType,
            @RequestPart(value = "chunkPolicy", required = false) String chunkPolicy) {
        return documentApplicationService.batchUploadDocuments(
                knowledgeBaseKey, files,
                toUploadRequest(processingMode, parserType, chunkPolicy));
    }

    // ────────────────────────────── 文档读入口（T012） ──────────────────────────────

    /**
     * 查询知识库下的文档当前视图。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param page             页码，从 1 开始
     * @param size             每页条数
     * @return 文档分页列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseKey}/documents")
    public R<PageResult<DocumentSummaryResponse>> listDocuments(
            @PathVariable @NotBlank String knowledgeBaseKey,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return documentApplicationService.listDocuments(knowledgeBaseKey, page, size);
    }

    /**
     * 查询文档详情。
     *
     * @param documentKey 文档标识
     * @return 文档详情响应
     */
    @GetMapping("/documents/{documentKey}")
    public R<DocumentDetailResponse> getDocumentDetail(@PathVariable @NotBlank String documentKey) {
        return documentApplicationService.getDocumentDetail(documentKey);
    }

    /**
     * 下载文档当前源文件。
     *
     * @param documentKey 文档标识
     * @return 文件流响应
     */
    @GetMapping("/documents/{documentKey}/file")
    public ResponseEntity<InputStreamResource> downloadDocumentFile(@PathVariable @NotBlank String documentKey) {
        DocumentFileContent content = documentApplicationService.downloadDocumentFile(documentKey);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(content.getFilename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(content.getContentType()))
                .contentLength(content.getSize())
                .body(new InputStreamResource(content.getInputStream()));
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
    private ChunkPolicyRequest parseChunkPolicy(String chunkPolicy) {
        if (chunkPolicy == null || chunkPolicy.isBlank()) {
            return null;
        }
        try {
            return JSON.parseObject(chunkPolicy, ChunkPolicyRequest.class);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID, exception);
        }
    }
}
