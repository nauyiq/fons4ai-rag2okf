package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.document.DocumentChunkApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.document.DocumentRechunkRequest;
import com.fons.cloud.ai.rag2okf.common.response.document.DocumentRechunkResponse;
import com.fons.cloud.common.result.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 基于当前 ParsedDocument 的重新分块接口。
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class RechunkController {

    private final DocumentChunkApplicationService documentChunkApplicationService;

    /**
     * 创建重新分块任务。请求携带当前结果版本，执行时会再次以版本和 stage 做 CAS 校验。
     *
     * @param documentKey 文档标识
     * @param request     重新分块请求
     * @return 重新分块任务受理响应
     */
    @PostMapping("/documents/{documentKey}/rechunk")
    public R<DocumentRechunkResponse> triggerRechunk(
            @PathVariable String documentKey,
            @RequestBody @Valid DocumentRechunkRequest request) {
        return documentChunkApplicationService.startRechunk(documentKey, request);
    }
}
