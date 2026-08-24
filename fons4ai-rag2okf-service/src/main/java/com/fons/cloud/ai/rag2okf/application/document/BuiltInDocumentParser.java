package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import org.springframework.stereotype.Component;

/**
 * Built-in 解析器启用骨架。
 *
 * <p>TP-002 期间仅以 {@link ParserAvailability#ENABLED} 状态满足上传链路的解析器
 * 可用性预检：上传创建的 PARSE 任务保持 QUEUED（无任务执行器），本方法不会被调用。
 * 真实的确定性提取、能力计划与模型增强链在 TP-003 以独立实现替换本骨架。</p>
 *
 * <h3>占位行为</h3>
 * <p>{@link #parse(ParseExecutionContext)} 抛出 {@code PARSE_UNEXPECTED_ERROR}：
 * 不存在"返回原文件即解析成功"的路径，任何未实现的实际解析必须显式失败，
 * 不得返回空结果或回退其他解析器。</p>
 *
 * @author hongqy
 */
@Component
public class BuiltInDocumentParser implements DocumentParser {

    @Override
    public ParserType type() {
        return ParserType.BUILT_IN;
    }

    @Override
    public ParserAvailability availability() {
        return ParserAvailability.ENABLED;
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context) {
        throw new DocumentProcessingException(Rag2OkfResultCode.PARSE_UNEXPECTED_ERROR);
    }
}
