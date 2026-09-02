package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import lombok.Getter;

import java.util.Map;

/** 上传后处理任务的冻结意图。 */
@Getter
public class DocumentUploadIntent {
    /** 是否创建解析任务。 */ private final boolean createParseTask;
    /** 冻结解析器类型。 */ private final ParserType parserType;
    /** 冻结分块策略。 */ private final ChunkPolicy chunkPolicy;
    /** 是否在解析成功后继续发布。 */ private final boolean publishAfterSuccess;
    /** 按用途索引的非敏感模型档案引用。 */ private final Map<String, ModelProfileReference> modelProfileRefs;
    public DocumentUploadIntent(boolean createParseTask, ParserType parserType, ChunkPolicy chunkPolicy,
                                boolean publishAfterSuccess, Map<String, ModelProfileReference> modelProfileRefs) {
        this.createParseTask = createParseTask; this.parserType = parserType; this.chunkPolicy = chunkPolicy;
        this.publishAfterSuccess = publishAfterSuccess; this.modelProfileRefs = Map.copyOf(modelProfileRefs);
    }
}
