package com.fons.cloud.ai.rag2okf.common.request.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 手动解析请求契约。
 *
 * @param parserType 解析器类型，本期只允许 BUILT_IN
 * @param chunkPolicy 首次分块策略，创建任务时冻结
 * @param publishAfterSuccess 解析与首次分块成功后是否继续发布
 */
public class DocumentParseRequest {

    /** 解析器类型，本期只允许已启用的 BUILT_IN。 */
    private ParserType parserType;

    /** 首次分块策略，创建任务时冻结。 */
    @Valid
    private ChunkPolicyRequest chunkPolicy;

    /** 解析和首次分块成功后是否继续发布。 */
    private boolean publishAfterSuccess;

    /** 无参构造器供 HTTP JSON 绑定使用。 */
    public DocumentParseRequest() {
    }

    /**
     * 创建手动解析请求。
     *
     * @param parserType 解析器类型
     * @param chunkPolicy 首次分块策略
     * @param publishAfterSuccess 是否在成功后继续发布
     */
    public DocumentParseRequest(
            ParserType parserType, ChunkPolicyRequest chunkPolicy, boolean publishAfterSuccess) {
        this.parserType = parserType;
        this.chunkPolicy = chunkPolicy;
        this.publishAfterSuccess = publishAfterSuccess;
    }

    /** @return 解析器类型。 */
    public ParserType getParserType() {
        return parserType;
    }

    /** @param parserType 解析器类型。 */
    public void setParserType(ParserType parserType) {
        this.parserType = parserType;
    }

    /** @return 首次分块策略。 */
    public ChunkPolicyRequest getChunkPolicy() {
        return chunkPolicy;
    }

    /** @param chunkPolicy 首次分块策略。 */
    public void setChunkPolicy(ChunkPolicyRequest chunkPolicy) {
        this.chunkPolicy = chunkPolicy;
    }

    /** @return 是否在首次分块成功后继续发布。 */
    public boolean isPublishAfterSuccess() {
        return publishAfterSuccess;
    }

    /** @param publishAfterSuccess 是否在首次分块成功后继续发布。 */
    public void setPublishAfterSuccess(boolean publishAfterSuccess) {
        this.publishAfterSuccess = publishAfterSuccess;
    }
}
