package com.fons.cloud.ai.rag2okf.infrastructure.adapter.document;

import com.fons.cloud.ai.rag2okf.application.document.DocumentParser;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserAvailability;
import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParseExecutionContext;
import com.fons.cloud.ai.rag2okf.common.model.document.RawParseResult;
import org.springframework.stereotype.Component;

/**
 * MinerU 解析器禁用骨架。
 *
 * <p>本期仅保留扩展契约和 {@link ParserAvailability#DISABLED} 可用性。
 * {@link #parse(ParseExecutionContext)} 在任何 IO（任务、MinIO、模型、网络）前抛出
 * {@code PARSER_NOT_AVAILABLE}，不创建任务、不调用远端服务、不回退 Built-in。</p>
 *
 * <h3>未来扩展边界</h3>
 * <p>接入真实 MinerU 时，在独立 Feature 中替换本骨架为启用适配器，补充 URL、API Key、
 * HTTP Client、远端 DTO、轮询或回调实现。本骨架不预留任何上述字段或依赖。</p>
 *
 * @author hongqy
 */
@Component
public class MinerUDocumentParserAdapter implements DocumentParser {

    /** 创建 MinerU 禁用骨架实例。 */
    public MinerUDocumentParserAdapter() {
        // 本骨架不持有任何状态、凭据或远端连接
    }

    @Override
    public ParserType type() {
        return ParserType.MINERU;
    }

    @Override
    public ParserAvailability availability() {
        return ParserAvailability.DISABLED;
    }

    @Override
    public RawParseResult parse(ParseExecutionContext context) {
        throw new DocumentProcessingException(Rag2OkfResultCode.PARSER_NOT_AVAILABLE);
    }
}
