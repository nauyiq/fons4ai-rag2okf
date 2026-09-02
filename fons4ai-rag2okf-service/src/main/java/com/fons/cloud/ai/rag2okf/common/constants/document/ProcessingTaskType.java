package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;

/**
 * 文档域异步处理任务类型。
 *
 * <p>严格枚举（设计 §5.3）：数据库列与请求参数只接受本枚举值，未知类型
 * 直接拒绝，不得默认回落 PARSE。任务默认最大执行次数按类型区分
 * （设计 §4.5）：PARSE/CHUNK/RECHUNK/PUBLISH 为 3 次，DELETE_CLEANUP 为 10 次。</p>
 *
 * @author hongqy
 */
public enum ProcessingTaskType {
    /** 仅解析并产出 ParsedDocument 的任务。 */
    PARSE("PARSE"),
    /** 首次分块任务：基于 PARSE 阶段的 ParsedDocument 生成 ChunkManifest。 */
    CHUNK("CHUNK"),
    /** 重新分块任务：基于已有 ParsedDocument 重新分块。 */
    RECHUNK("RECHUNK"),
    /** 发布任务：向量化并写入 ES 投影。 */
    PUBLISH("PUBLISH"),
    /** 删除内容物理清理任务：软删除受理后异步清理 MinIO 制品与 ES 投影。 */
    DELETE_CLEANUP("DELETE_CLEANUP");

    /** 与数据库列保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    ProcessingTaskType(String value) {
        this.value = value;
    }

    /** 获取持久化代码值。 */
    public String getValue() {
        return value;
    }

    /**
     * 按持久化代码解析任务类型。
     *
     * <p>用于任务调度、追溯查询等从数据库或请求恢复任务类型的场景；
     * 未知代码视为输入非法，明确失败而非猜测回落。</p>
     *
     * @param value 数据库或请求中的任务类型代码
     * @return 对应的任务类型枚举
     * @throws DocumentProcessingException 未知类型代码时抛出，错误码为任务输入快照无效
     */
    public static ProcessingTaskType fromValue(String value) {
        for (ProcessingTaskType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
    }

    /**
     * 获取该类型任务的默认最大执行次数。
     *
     * <p>DELETE_CLEANUP 清理失败影响存储回收且可安全重复执行，容忍更多次重试；
     * 业务处理类任务默认 3 次，终态失败后通过重新发起新任务追溯关联。</p>
     *
     * @return 默认最大执行次数
     */
    public int defaultMaxAttempts() {
        return this == DELETE_CLEANUP ? 10 : 3;
    }
}
