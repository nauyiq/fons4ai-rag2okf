package com.fons.cloud.ai.rag2okf.infrastructure.document.parser.strategy;

import com.fons.cloud.ai.rag2okf.common.model.document.DocumentFileCapability;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;

/**
 * 单一已识别文件类别的确定性解析策略。
 *
 * <p>策略只产生按源顺序组织的 RawParseResult，不生成 ChunkManifest 或自行选择其他 Parser。</p>
 *
 * @author hongqy
 */
public interface DocumentParseStrategy {

    /**
     * 判断策略是否支持已冻结的文件能力。
     *
     * @param capability 上传预检确认的文件能力
     * @return 支持时返回 true
     */
    boolean supports(DocumentFileCapability capability);

    /**
     * 提取一种文件类别的原始结构材料。
     *
     * @param context 受控解析上下文
     * @param capability 上传预检确认的文件能力
     * @return 有序原始内容块
     */
    RawParseResult parse(ParseExecutionContext context, DocumentFileCapability capability);
}
