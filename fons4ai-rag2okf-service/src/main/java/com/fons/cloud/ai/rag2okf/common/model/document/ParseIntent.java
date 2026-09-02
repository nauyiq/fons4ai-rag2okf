package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;

import java.util.List;
import java.util.Objects;

/**
 * 已校验源文件的解析意图。
 *
 * <p>该对象在读取实际解析内容前冻结文件类别、目标解析器和能力候选；
 * 不保存正文、对象存储键、连接信息或模型凭证。</p>
 *
 * @param fileCategory 文件类别
 * @param parserType 唯一目标解析器
 * @param requiredCapabilities 必要能力候选
 * @param enhancementCapabilities 可选增强能力候选
 * @param reasons 安全化识别依据
 * @author hongqy
 */
public record ParseIntent(
        String fileCategory,
        ParserType parserType,
        List<String> requiredCapabilities,
        List<String> enhancementCapabilities,
        List<String> reasons) {

    public ParseIntent {
        if (fileCategory == null || fileCategory.isBlank()) {
            throw new IllegalArgumentException("fileCategory must not be blank");
        }
        Objects.requireNonNull(parserType, "parserType must not be null");
        requiredCapabilities = requiredCapabilities == null ? List.of() : List.copyOf(requiredCapabilities);
        enhancementCapabilities = enhancementCapabilities == null ? List.of() : List.copyOf(enhancementCapabilities);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }
}
