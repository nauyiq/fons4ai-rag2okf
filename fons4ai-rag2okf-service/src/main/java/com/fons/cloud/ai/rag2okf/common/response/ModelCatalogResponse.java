package com.fons.cloud.ai.rag2okf.common.response;

import java.util.List;
import java.util.Map;

/**
 * 模型目录只读响应。
 *
 * @param providers 厂商清单
 * @param typeCounts 按 modelType 统计的模型数量
 * @author hongqy
 */
public record ModelCatalogResponse(
        List<CatalogProvider> providers,
        Map<String, Integer> typeCounts
) {

    /** 厂商目录项。 */
    public record CatalogProvider(
            String providerCode,
            String providerName,
            String defaultBaseUrl,
            String officialUrl,
            List<CatalogModel> models
    ) {
    }

    /** 模型目录项。 */
    public record CatalogModel(
            String modelName,
            String modelType
    ) {
    }
}
