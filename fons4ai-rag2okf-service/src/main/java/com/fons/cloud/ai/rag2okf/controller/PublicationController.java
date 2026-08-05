package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.publication.PublicationApplicationService;
import com.fons.cloud.ai.rag2okf.common.response.PublicationResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文档发布接口（AC-015、AC-016、AC-017、AC-018）。
 *
 * <p>Controller 只承担 HTTP 入参/出参转换，不承载业务规则。
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationApplicationService publicationApplicationService;

    /**
     * 发起发布。对当前成功分块创建发布任务（AC-015、AC-016、AC-017）。
     *
     * @param documentKey 文档标识
     * @param triggerType 触发方式：MANUAL（默认）或 AUTO
     * @return 发布受理响应
     */
    @PostMapping("/documents/{documentKey}/publish")
    public R<PublicationResponse> triggerPublish(
            @PathVariable String documentKey,
            @RequestParam(value = "triggerType", required = false) String triggerType) {
        return R.ok(publicationApplicationService.triggerPublish(
                documentKey, triggerType != null ? triggerType : "MANUAL"));
    }
}
