package com.fons.cloud.ai.rag2okf.controller;

import com.fons.cloud.ai.rag2okf.application.model.ModelCatalogApplicationService;
import com.fons.cloud.ai.rag2okf.common.response.ModelCatalogResponse;
import com.fons.cloud.common.result.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模型目录只读 HTTP 接口。
 *
 * @author hongqy
 */
@RestController
@RequiredArgsConstructor
public class ModelCatalogController {

    private final ModelCatalogApplicationService modelCatalogApplicationService;

    /** @return 厂商清单与按 modelType 统计的数量 */
    @GetMapping("/model-catalog")
    public R<ModelCatalogResponse> getCatalog() {
        return R.ok(modelCatalogApplicationService.getCatalog());
    }
}
