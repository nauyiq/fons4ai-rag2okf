package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;

/**
 * 上传处理模式。
 *
 * <p>表达用户对本次上传的处理意图（设计 §3.2 上传契约）：
 * {@code DEFAULT} 按知识库默认策略决定是否创建 PARSE 任务，{@code PARSE}
 * 立即创建 PARSE 任务，{@code SKIP} 仅保存源文件不创建任务。</p>
 *
 * @author hongqy
 */
public enum ProcessingMode {
    /** 按知识库默认：提交时读取 autoParse/autoPublish 冻结进任务快照，运行时不重读。 */
    DEFAULT("DEFAULT"),
    /** 立即解析：无条件创建 PARSE 任务。 */
    PARSE("PARSE"),
    /** 仅保存：只登记文档与源文件，不创建任何处理任务。 */
    SKIP("SKIP");

    /** 与请求参数和数据库列保持兼容的代码值。 */
    private final String value;

    ProcessingMode(String value) {
        this.value = value;
    }

    /** 获取代码值。 */
    public String getValue() {
        return value;
    }

    /**
     * 按请求参数解析处理模式。
     *
     * <p>未知代码视为输入非法，明确失败而非猜测回落。</p>
     *
     * @param value 请求中的处理模式代码
     * @return 对应的处理模式枚举
     * @throws DocumentProcessingException 未知代码时抛出
     */
    public static ProcessingMode fromValue(String value) {
        for (ProcessingMode mode : values()) {
            if (mode.value.equals(value)) {
                return mode;
            }
        }
        throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
    }
}
