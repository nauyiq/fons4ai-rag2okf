package com.fons.cloud.ai.rag2okf.common.constants.document;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;

/**
 * 文档解析器类型。
 *
 * <p>本期只允许 {@link #BUILT_IN} 进入实际解析链路；{@link #MINERU} 仅保留扩展契约和禁用适配器骨架，
 * 任何 MinerU 请求都必须返回 {@code PARSER_NOT_AVAILABLE}，不创建任务、不调用远端服务、不回退 Built-in。</p>
 *
 * @author hongqy
 */
public enum ParserType {
    /** Built-in 解析器，使用文件分类→确定性提取→能力计划→按需模型调用→规范化的内置工作流。 */
    BUILT_IN("BUILT_IN"),
    /** MinerU 解析器，本期仅保留扩展契约和禁用适配器骨架，不开放真实解析。 */
    MINERU("MINERU");

    /** 与数据库列保持兼容的持久化代码。 */
    @EnumValue
    private final String value;

    ParserType(String value) {
        this.value = value;
    }

    /** 获取持久化代码值。 */
    public String getValue() {
        return value;
    }

    /** 判断当前解析器类型本期是否已开放实际解析。 */
    public boolean isAvailable() {
        return this == BUILT_IN;
    }

    /**
     * 按请求参数严格解析解析器类型。
     *
     * <p>未知代码视为输入非法，明确失败而非猜测回落，供 HTTP 入参解析使用。</p>
     *
     * @param value 请求中的解析器类型代码
     * @return 对应的解析器类型枚举
     * @throws DocumentProcessingException 未知代码时抛出，携带 {@code PAYLOAD_INVALID}
     */
    public static ParserType fromValue(String value) {
        for (ParserType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new DocumentProcessingException(Rag2OkfResultCode.PAYLOAD_INVALID);
    }
}
