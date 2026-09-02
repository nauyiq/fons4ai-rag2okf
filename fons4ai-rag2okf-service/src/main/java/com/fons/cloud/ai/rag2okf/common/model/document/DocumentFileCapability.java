package com.fons.cloud.ai.rag2okf.common.model.document;

import java.util.List;
import java.util.Objects;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

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
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@EqualsAndHashCode
public class DocumentFileCapability {

    /** 文件类别，例如纯文本、PDF、图片或音频。 */
    private String category;
    /** 小写且不带点的文件扩展名。 */
    private String extension;
    /** 服务端允许的 MIME 类型白名单。 */
    private List<String> mimeWhitelist;
    /** 不依赖模型的确定性基础提取方式。 */
    private String baseExtraction;
    /** 缺失时必须令解析失败的模型能力。 */
    private List<String> requiredCapabilities;
    /** 缺失时允许保留基础结果并返回警告的模型能力。 */
    private List<String> enhancementCapabilities;

    /**
     * 创建一项不可变能力清单。
     *
     * @param category 文件类别
     * @param extension 文件扩展名
     * @param mimeWhitelist MIME 白名单
     * @param baseExtraction 基础提取方式
     * @param requiredCapabilities 必要能力
     * @param enhancementCapabilities 可选增强能力
     */
    public DocumentFileCapability(String category, String extension, List<String> mimeWhitelist,
                                  String baseExtraction, List<String> requiredCapabilities,
                                  List<String> enhancementCapabilities) {
        this.category = Objects.requireNonNull(category, "category must not be null");
        this.extension = Objects.requireNonNull(extension, "extension must not be null");
        this.mimeWhitelist = List.copyOf(Objects.requireNonNull(mimeWhitelist));
        this.baseExtraction = Objects.requireNonNull(baseExtraction, "baseExtraction must not be null");
        this.requiredCapabilities = List.copyOf(Objects.requireNonNull(requiredCapabilities));
        this.enhancementCapabilities = List.copyOf(Objects.requireNonNull(enhancementCapabilities));
    }
}
