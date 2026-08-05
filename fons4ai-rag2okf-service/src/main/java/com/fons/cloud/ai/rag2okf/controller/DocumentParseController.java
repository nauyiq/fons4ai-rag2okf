package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.parsing.ParseApplicationService;
import com.fons.cloud.ai.rag2okf.common.response.ChunkPreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParsePreviewResponse;
import com.fons.cloud.ai.rag2okf.common.response.ParseTriggerResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档解析、预览接口（AC-006、AC-011、AC-012、AC-013、AC-014）。
 *
 * <p>Controller 只承担 HTTP 入参/出参转换，不承载业务规则。
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class DocumentParseController {

    private final ParseApplicationService parseApplicationService;

    /**
     * 发起解析。手动触发，创建或复用解析任务（AC-006、AC-011）。
     *
     * @param documentKey 文档标识
     * @param parseMode   解析模式：DEFAULT、PARSE 或 SKIP
     * @return 解析受理响应
     */
    @PostMapping("/documents/{documentKey}/parse")
    public R<ParseTriggerResponse> triggerParse(
            @PathVariable String documentKey,
            @RequestParam(value = "parseMode", required = false) String parseMode) {
        return R.ok(parseApplicationService.triggerParse(
                documentKey, parseMode != null ? parseMode : "DEFAULT"));
    }

    /**
     * 解析预览。返回结构化 block 与 SourceAnchor（AC-012、AC-013）。
     *
     * @param documentKey 文档标识
     * @return 解析预览响应
     */
    @GetMapping("/documents/{documentKey}/parse-preview")
    public R<ParsePreviewResponse> getParsePreview(@PathVariable String documentKey) {
        return R.ok(parseApplicationService.getParsePreview(documentKey));
    }

    /**
     * 分块预览。返回当前解析侧分块分页（AC-012、AC-013）。
     *
     * @param documentKey 文档标识
     * @param page        页码（从 0 开始，默认 0）
     * @param size        每页大小（默认 20）
     * @return 分块预览响应
     */
    @GetMapping("/documents/{documentKey}/chunks")
    public R<ChunkPreviewResponse> getChunkPreview(
            @PathVariable String documentKey,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return R.ok(parseApplicationService.getChunkPreview(documentKey, page, size));
    }
}
