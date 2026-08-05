package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.chunking.RechunkApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.RechunkRequest;
import com.fons.cloud.ai.rag2okf.common.response.RechunkResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 重新分块接口（AC-019、AC-020、AC-021）。
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class RechunkController {

    private final RechunkApplicationService rechunkApplicationService;

    /**
     * 重新分块。明确确认后重建当前解析侧分块。
     *
     * @param documentKey 文档标识
     * @param request     重新分块请求
     * @return 重新分块受理响应
     */
    @PostMapping("/documents/{documentKey}/rechunk")
    public R<RechunkResponse> triggerRechunk(
            @PathVariable String documentKey,
            @RequestBody RechunkRequest request) {
        return R.ok(rechunkApplicationService.triggerRechunk(
                documentKey, request.confirmed(),
                request.expectedChunkRevisionKey(), request.chunkProfile()));
    }
}
