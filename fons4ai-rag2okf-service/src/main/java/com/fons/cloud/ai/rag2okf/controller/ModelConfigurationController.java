package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.model.ModelConfigurationApplicationService;
import com.fons.cloud.ai.rag2okf.common.request.CreateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.CreateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.common.request.ReplaceApiKeyRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateModelConnectionRequest;
import com.fons.cloud.ai.rag2okf.common.request.UpdateModelProfileRequest;
import com.fons.cloud.ai.rag2okf.common.response.ModelConnectionResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelProfileResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelProviderTemplateResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelTestResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户模型连接与档案的 HTTP 接口。
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class ModelConfigurationController {

    private final ModelConfigurationApplicationService modelConfigurationApplicationService;

    /** @return P0 Provider 模板 */
    @GetMapping("/model-provider-templates")
    public R<List<ModelProviderTemplateResponse>> listTemplates() {
        return R.ok(modelConfigurationApplicationService.listTemplates());
    }

    /** @return 当前用户拥有的 Provider 连接 */
    @GetMapping("/model-connections")
    public R<List<ModelConnectionResponse>> listConnections() {
        return R.ok(modelConfigurationApplicationService.listConnections());
    }

    /** @param request 创建连接请求 @return 不含凭证的连接 */
    @PostMapping("/model-connections")
    public R<ModelConnectionResponse> createConnection(@RequestBody CreateModelConnectionRequest request) {
        return R.ok(modelConfigurationApplicationService.createConnection(request));
    }

    /** @param connectionKey 连接标识 @param request 更新请求 @return 更新后的连接 */
    @PatchMapping("/model-connections/{connectionKey}")
    public R<ModelConnectionResponse> updateConnection(@PathVariable String connectionKey,
                                                        @RequestBody UpdateModelConnectionRequest request) {
        return R.ok(modelConfigurationApplicationService.updateConnection(connectionKey, request));
    }

    /** @param connectionKey 连接标识 @param request 替换 API Key 请求 @return 更新后的连接 */
    @PatchMapping("/model-connections/{connectionKey}/api-key")
    public R<ModelConnectionResponse> replaceApiKey(@PathVariable String connectionKey,
                                                     @RequestBody ReplaceApiKeyRequest request) {
        return R.ok(modelConfigurationApplicationService.replaceApiKey(connectionKey, request.apiKey()));
    }

    /** @param connectionKey 连接标识 @return 删除结果 */
    @DeleteMapping("/model-connections/{connectionKey}")
    public R<Void> deleteConnection(@PathVariable String connectionKey) {
        modelConfigurationApplicationService.deleteConnection(connectionKey);
        return R.ok(null);
    }

    /** @param connectionKey 所属连接标识，可选过滤条件 @return 当前用户拥有的模型档案 */
    @GetMapping("/model-profiles")
    public R<List<ModelProfileResponse>> listProfiles(
            @RequestParam(value = "connectionKey", required = false) String connectionKey) {
        return R.ok(modelConfigurationApplicationService.listProfiles(connectionKey));
    }

    /** @param request 创建档案请求 @return 不含凭证的档案 */
    @PostMapping("/model-profiles")
    public R<ModelProfileResponse> createProfile(@RequestBody CreateModelProfileRequest request) {
        return R.ok(modelConfigurationApplicationService.createProfile(request));
    }

    /** @param profileKey 档案标识 @param request 更新请求 @return 更新后的档案 */
    @PatchMapping("/model-profiles/{profileKey}")
    public R<ModelProfileResponse> updateProfile(@PathVariable("profileKey") String profileKey,
                                                  @RequestBody UpdateModelProfileRequest request) {
        return R.ok(modelConfigurationApplicationService.updateProfile(profileKey, request));
    }

    /** @param profileKey 档案标识 @return 删除结果 */
    @DeleteMapping("/model-profiles/{profileKey}")
    public R<Void> deleteProfile(@PathVariable("profileKey") String profileKey) {
        modelConfigurationApplicationService.deleteProfile(profileKey);
        return R.ok(null);
    }

    /** @param profileKey 档案标识 @return 不泄露 Provider 原始错误的测试结果 */
    @PostMapping("/model-profiles/{profileKey}/test")
    public R<ModelTestResponse> testProfile(@PathVariable("profileKey") String profileKey) {
        return R.ok(modelConfigurationApplicationService.testProfile(profileKey));
    }
}
