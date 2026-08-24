package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 文档结果生命周期阶段白名单。
 *
 * <p>stage 表达 {@code kb_document_result} 已成功推进到的内部阶段，只允许按
 * INIT → PARSE → CHUNK → PUBLISH 顺序推进；发布失败时允许 PUBLISH 回退 CHUNK，
 * 发布成功写投影元数据时允许 PUBLISH 保持不变（版本递增）：</p>
 *
 * <ul>
 *   <li>{@code INIT}：上传完成，源文件指针已写入，未开始解析</li>
 *   <li>{@code PARSE}：解析成功，ParsedDocument 已写入 MinIO</li>
 *   <li>{@code CHUNK}：分块成功，ChunkManifest 已写入 MinIO</li>
 *   <li>{@code PUBLISH}：发布成功，ES 投影校验通过</li>
 * </ul>
 *
 * <p>阶段跳跃（如 INIT→CHUNK）属于非法转移；失败原因不在 result 表表达，
 * 由 {@code kb_processing_task} 承载。</p>
 *
 * @author hongqy
 */
@Getter
@AllArgsConstructor
public enum ResultStage {

    /** 上传完成，源文件指针已写入，未开始解析。 */
    INIT("INIT"),
    /** 解析成功，ParsedDocument 已写入 MinIO，分块尚未完成。 */
    PARSE("PARSE"),
    /** 分块成功，ChunkManifest 已写入 MinIO，尚未发布。 */
    CHUNK("CHUNK"),
    /** 发布成功，ES 投影已通过写入与校验。 */
    PUBLISH("PUBLISH");

    /** 与数据库列保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    /**
     * 判断是否允许从当前阶段推进到目标阶段。
     *
     * <p>包含两条特殊转移：PUBLISH→PUBLISH（发布成功写投影元数据，版本递增）
     * 与 PUBLISH→CHUNK（发布失败回退到分块阶段）。</p>
     *
     * @param target 目标阶段
     * @return 在顺序推进白名单内时返回 {@code true}
     */
    public boolean canAdvanceTo(ResultStage target) {
        return switch (this) {
            case INIT -> target == PARSE;
            case PARSE -> target == CHUNK;
            case CHUNK -> target == PUBLISH;
            case PUBLISH -> target == PUBLISH || target == CHUNK;
        };
    }
}
