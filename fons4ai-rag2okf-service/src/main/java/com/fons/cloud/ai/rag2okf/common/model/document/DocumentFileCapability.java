package com.fons.cloud.ai.rag2okf.common.model.document;

import java.util.List;
import java.util.Objects;

/**
 * Built-in 文件能力描述。
 *
 * <p>每个记录实例描述一种已启用文件格式的能力计划：</p>
 * <ul>
 *   <li>{@code category} — 文件类别（纯文本、Markdown、PDF、Word、图片、音频）</li>
 *   <li>{@code extension} — 文件扩展名（不含点，小写）</li>
 *   <li>{@code mimeWhitelist} — 允许的 MIME 类型白名单</li>
 *   <li>{@code baseExtraction} — 基础提取方式描述</li>
 *   <li>{@code requiredCapabilities} — 必要模型能力列表，缺失则解析失败</li>
 *   <li>{@code enhancementCapabilities} — 增强模型能力列表，缺失可带警告完成</li>
 * </ul>
 *
 * <p>由 {@code BuiltInFileCapabilityCatalog} 根据文件扩展名和服务端探测的 MIME 类型返回。
 * 所有列表字段不可变且拒绝 null。</p>
 *
 * @author hongqy
 */
public record DocumentFileCapability(
    String category,
    String extension,
    List<String> mimeWhitelist,
    String baseExtraction,
    List<String> requiredCapabilities,
    List<String> enhancementCapabilities
) {

    /** 紧凑构造器，防御性拷贝列表参数并拒绝空值。 */
    public DocumentFileCapability {
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
        Objects.requireNonNull(baseExtraction, "baseExtraction must not be null");
        mimeWhitelist = List.copyOf(Objects.requireNonNull(mimeWhitelist));
        requiredCapabilities = List.copyOf(Objects.requireNonNull(requiredCapabilities));
        enhancementCapabilities = List.copyOf(Objects.requireNonNull(enhancementCapabilities));
    }
}
