package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.document.DocumentApplicationService;
import com.fons.cloud.ai.rag2okf.application.document.DocumentApplicationService.DocumentFileContent;
import com.fons.cloud.ai.rag2okf.common.response.DocumentDetailResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.DocumentUploadResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

/**
 * 文档上传、更新文件、详情查询与原文件下载的 HTTP 接口。
 *
 * <p>Controller 只承担 HTTP 入参/出参转换，不承载业务规则。
 * 响应不返回版本列表或回退操作（D-004）。</p>
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentApplicationService documentApplicationService;

    /** 查询知识库下的文档当前视图，不返回历史版本。 */
    @GetMapping("/knowledge-bases/{knowledgeBaseKey}/documents")
    public R<PageResponse<DocumentSummaryResponse>> listDocuments(
            @PathVariable String knowledgeBaseKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(documentApplicationService.listDocuments(knowledgeBaseKey, page, size));
    }

    /**
     * 上传新文档。同名文件不合并，生成新的 documentKey。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param file 上传文件
     * @param parseMode 解析模式：DEFAULT、PARSE 或 SKIP
     * @return 上传受理响应
     */
    @PostMapping("/knowledge-bases/{knowledgeBaseKey}/documents")
    public R<DocumentUploadResponse> uploadDocument(
            @PathVariable String knowledgeBaseKey,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "parseMode", required = false) String parseMode) {
        return R.ok(documentApplicationService.uploadDocument(
                knowledgeBaseKey, file, parseMode != null ? parseMode : "DEFAULT"));
    }

    /**
     * 更新文档的当前文件。使用 expectedCurrentVersionKey 做乐观控制。
     *
     * @param documentKey 文档标识
     * @param file 新文件
     * @param parseMode 解析模式
     * @param expectedCurrentVersionKey 调用方持有的当前版本 key
     * @return 更新受理响应
     */
    @PostMapping("/documents/{documentKey}/files")
    public R<DocumentUploadResponse> updateDocumentFile(
            @PathVariable String documentKey,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "parseMode", required = false) String parseMode,
            @RequestPart("expectedCurrentFileToken") String expectedCurrentFileToken) {
        return R.ok(documentApplicationService.updateDocumentFile(
                documentKey, file,
                parseMode != null ? parseMode : "DEFAULT",
                expectedCurrentFileToken));
    }

    /**
     * 查询文档详情。不返回版本列表（D-004）。
     *
     * @param documentKey 文档标识
     * @return 文档详情响应
     */
    @GetMapping("/documents/{documentKey}")
    public R<DocumentDetailResponse> getDocumentDetail(@PathVariable String documentKey) {
        return R.ok(documentApplicationService.getDocumentDetail(documentKey));
    }

    /**
     * 下载文档当前原文件。需要 USER 权限。
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
}
