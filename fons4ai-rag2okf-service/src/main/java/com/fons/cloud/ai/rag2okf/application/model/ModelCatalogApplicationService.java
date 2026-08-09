package com.fons.cloud.ai.rag2okf.application.model;

import com.fons.cloud.ai.rag2okf.common.response.ModelCatalogResponse;
import com.fons.cloud.ai.rag2okf.common.response.ModelCatalogResponse.CatalogModel;
import com.fons.cloud.ai.rag2okf.common.response.ModelCatalogResponse.CatalogProvider;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型目录只读应用服务。
 *
 * <p>从 classpath:model-catalog.yaml 加载厂商与模型清单，本地缓存 10 分钟。
 * 不承载用户凭证，仅用于前端选择 Provider 与模型时的下拉数据源。</p>
 *
 * @author hongqy
 */
@Service
public class ModelCatalogApplicationService {

    /** 本地缓存有效期（毫秒）。 */
    private static final long CACHE_TTL_MILLIS = 10 * 60 * 1000L;
    private static final String CLASSPATH_RESOURCE = "model-catalog.yaml";

    private volatile ModelCatalogResponse cached;
    private volatile long cachedAt;

    /**
     * 获取模型目录，包含厂商清单与按 modelType 统计的数量。
     *
     * @return 模型目录响应
     */
    public ModelCatalogResponse getCatalog() {
        ModelCatalogResponse snapshot = cached;
        long now = System.currentTimeMillis();
        if (snapshot != null && now - cachedAt < CACHE_TTL_MILLIS) {
            return snapshot;
        }
        synchronized (this) {
            if (cached != null && now - cachedAt < CACHE_TTL_MILLIS) {
                return cached;
            }
            ModelCatalogResponse loaded = loadFromYaml();
            cached = loaded;
            cachedAt = now;
            return loaded;
        }
    }

    private ModelCatalogResponse loadFromYaml() {
        YamlMapFactoryBean factory = new YamlMapFactoryBean();
        factory.setResources(new ClassPathResource(CLASSPATH_RESOURCE));
        Map<String, Object> root = factory.getObject();
        List<CatalogProvider> providers = new ArrayList<>();
        Map<String, Integer> typeCounts = new LinkedHashMap<>();
        if (root != null) {
            Object providersRaw = root.get("providers");
            if (providersRaw instanceof List<?> list) {
                for (Object item : list) {
                    if (!(item instanceof Map<?, ?> pmap)) {
                        continue;
                    }
                    List<CatalogModel> models = new ArrayList<>();
                    Object modelsRaw = pmap.get("models");
                    if (modelsRaw instanceof List<?> mlist) {
                        for (Object m : mlist) {
                            if (!(m instanceof Map<?, ?> mmap)) {
                                continue;
                            }
                            String modelName = str(mmap.get("modelName"));
                            String modelType = str(mmap.get("modelType"));
                            models.add(new CatalogModel(modelName, modelType));
                            if (modelType != null) {
                                typeCounts.merge(modelType, 1, Integer::sum);
                            }
                        }
                    }
                    providers.add(new CatalogProvider(
                            str(pmap.get("providerCode")),
                            str(pmap.get("providerName")),
                            str(pmap.get("defaultBaseUrl")),
                            str(pmap.get("officialUrl")),
                            models
                    ));
                }
            }
        }
        return new ModelCatalogResponse(providers, typeCounts);
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
