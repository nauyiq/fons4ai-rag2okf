package com.fons.cloud.ai.rag2okf.common.constants;

import com.baomidou.mybatisplus.annotation.EnumValue;

import java.util.List;

/**
 * 模型档案提供的调用能力类型。
 *
 * <p>白名单含 7 个有效值：LLM、EMBEDDING、RERANK、TTS、ASR、VLM、OCR。
 * 旧值 CHAT 仅用于读取历史数据时的别名兼容，不再允许新写入；
 * 读取时通过 {@link #normalize(String)} 将 CHAT 归一为 LLM。</p>
 *
 * @author hongqy
 */
public enum ModelType {
    /** 文本生成能力（旧称 CHAT）。 */
    LLM("LLM"),
    /** 文本向量化能力。 */
    EMBEDDING("EMBEDDING"),
    /** 重排序能力。 */
    RERANK("RERANK"),
    /** 语音合成能力。 */
    TTS("TTS"),
    /** 语音识别能力。 */
    ASR("ASR"),
    /** 视觉语言能力。 */
    VLM("VLM"),
    /** 图像文字识别能力。 */
    OCR("OCR"),
    /** 旧版对话生成能力，仅用于读取别名兼容，不用于新写入。 */
    CHAT("CHAT");

    @EnumValue
    private final String value;

    ModelType(String value) {
        this.value = value;
    }

    /**
     * 获取数据库持久化代码。
     *
     * @return 模型类型代码
     */
    public String getValue() {
        return value;
    }

    /**
     * 判断给定值是否在 7 个有效白名单内（不含旧值 CHAT）。
     *
     * @param value 模型类型代码
     * @return 是否在白名单内
     */
    public static boolean isValid(String value) {
        return value != null && whitelist().contains(value);
    }

    /**
     * 读取别名归一：CHAT → LLM，其他值原样返回（不在白名单也不抛错）。
     *
     * @param value 原始模型类型代码
     * @return 归一后的模型类型代码
     */
    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        return "CHAT".equals(value) ? "LLM" : value;
    }

    /**
     * 返回 7 个有效模型类型代码列表（不含旧值 CHAT）。
     *
     * @return 白名单列表
     */
    public static List<String> whitelist() {
        return List.of("LLM", "EMBEDDING", "RERANK", "TTS", "ASR", "VLM", "OCR");
    }
}
