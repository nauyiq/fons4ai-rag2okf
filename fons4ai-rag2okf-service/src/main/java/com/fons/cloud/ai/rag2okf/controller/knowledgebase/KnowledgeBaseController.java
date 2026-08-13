package com.fons.cloud.ai.rag2okf.controller.knowledgebase;

import com.fons.cloud.ai.rag2okf.application.knowledgebase.KnowledgeBaseApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.CreateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.request.SaveModelBindingsRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateKnowledgeBaseRequest;
import com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseResponse;
import com.fons.cloud.ai.rag2okf.common.response.KnowledgeBaseSummaryResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelBindingResponse;
import com.fons.cloud.ai.rag2okf.common.response.PageResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库创建、列表、详情、设置编辑与模型用途绑定的 HTTP 接口。
 *
 * <p>Controller 只承担 HTTP 入参/出参转换，不承载业务规则。</p>
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseApplicationService knowledgeBaseApplicationService;

    /**
     * 分页查询知识库列表。
     *
     * @param workspaceKey 工作空间标识
     * @param page 页码（0 基）
     * @param size 每页大小
     * @return 分页知识库摘要
     */
    @GetMapping("/workspaces/{workspaceKey}/knowledge-bases")
    public R<PageResponse<KnowledgeBaseSummaryResponse>> listKnowledgeBases(
            @PathVariable String workspaceKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(knowledgeBaseApplicationService.listKnowledgeBases(workspaceKey, page, size));
    }

    /**
     * 创建知识库。
     *
     * @param workspaceKey 工作空间标识
     * @param request 创建请求
     * @return 知识库详情响应
     */
    @PostMapping("/workspaces/{workspaceKey}/knowledge-bases")
    public R<KnowledgeBaseResponse> createKnowledgeBase(
            @PathVariable String workspaceKey,
            @RequestBody CreateKnowledgeBaseRequest request) {
        return R.ok(knowledgeBaseApplicationService.createKnowledgeBase(workspaceKey, request));
    }

    /**
     * 查询知识库详情。
     *
     * @param knowledgeBaseKey 知识库标识
     * @return 知识库详情响应
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseKey}")
    public R<KnowledgeBaseResponse> getKnowledgeBase(@PathVariable String knowledgeBaseKey) {
        return R.ok(knowledgeBaseApplicationService.getKnowledgeBase(knowledgeBaseKey));
    }

    /**
     * 编辑知识库信息与默认处理设置。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param request 更新请求
     * @return 更新后的知识库详情响应
     */
    @PatchMapping("/knowledge-bases/{knowledgeBaseKey}")
    public R<KnowledgeBaseResponse> updateKnowledgeBase(
            @PathVariable String knowledgeBaseKey,
            @RequestBody UpdateKnowledgeBaseRequest request) {
        return R.ok(knowledgeBaseApplicationService.updateKnowledgeBase(knowledgeBaseKey, request));
    }

    /**
     * 删除知识库（软删除）。
     *
     * <p>仅创建者可删除。命中已删除或不存在的知识库时幂等返回成功。</p>
     *
     * @param knowledgeBaseKey 知识库标识
     * @return 统一响应包装
     */
    @DeleteMapping("/knowledge-bases/{knowledgeBaseKey}")
    public R<Void> deleteKnowledgeBase(@PathVariable String knowledgeBaseKey) {
        knowledgeBaseApplicationService.deleteKnowledgeBase(knowledgeBaseKey);
        return R.ok(null);
    }

    /**
     * 查询知识库模型用途绑定。
     *
     * @param knowledgeBaseKey 知识库标识
     * @return 模型用途绑定列表
     */
    @GetMapping("/knowledge-bases/{knowledgeBaseKey}/model-bindings")
    public R<List<ModelBindingResponse>> getModelBindings(@PathVariable String knowledgeBaseKey) {
        return R.ok(knowledgeBaseApplicationService.getModelBindings(knowledgeBaseKey));
    }

    /**
     * 整体保存知识库模型用途绑定。
     *
     * @param knowledgeBaseKey 知识库标识
     * @param request 保存请求
     * @return 保存后的模型用途绑定列表
     */
    @PutMapping("/knowledge-bases/{knowledgeBaseKey}/model-bindings")
    public R<List<ModelBindingResponse>> saveModelBindings(
            @PathVariable String knowledgeBaseKey,
            @RequestBody SaveModelBindingsRequest request) {
        return R.ok(knowledgeBaseApplicationService.saveModelBindings(knowledgeBaseKey, request));
    }
}
