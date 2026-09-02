package com.fons.cloud.ai.rag2okf.infrastructure.document.chunking;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONWriter;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ChunkManifest;
import org.springframework.stereotype.Component;

/**
 * ChunkManifest 的稳定 JSON 编解码器。
 *
 * @author hongqy
 */
@Component
public class ChunkManifestCodec {

    /**
     * 编码清单为字节稳定 JSON。
     *
     * @param manifest 已校验清单
     * @return JSON 字节
     */
    public byte[] writeJson(ChunkManifest manifest) {
        try {
            return JSON.toJSONBytes(manifest, JSONWriter.Feature.MapSortField);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.RECHUNK_ARTIFACT_ERROR, exception);
        }
    }

    /**
     * 从 JSON 读取清单。
     *
     * @param content JSON 字节
     * @return 清单对象
     */
    public ChunkManifest readJson(byte[] content) {
        try {
            return JSON.parseObject(content, ChunkManifest.class);
        } catch (RuntimeException exception) {
            throw new DocumentProcessingException(Rag2OkfResultCode.RECHUNK_ARTIFACT_ERROR, exception);
        }
    }
}
