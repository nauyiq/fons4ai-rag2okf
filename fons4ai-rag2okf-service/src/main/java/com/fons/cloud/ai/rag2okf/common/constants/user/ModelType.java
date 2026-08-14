package com.fons.cloud.ai.rag2okf.common.constants.user;

import com.baomidou.mybatisplus.annotation.EnumValue;

import java.util.Arrays;
import java.util.List;

/**
 * 模型档案提供的调用能力类型。
 *
 * <p>白名单含 7 个有效值：LLM、EMBEDDING、RERANK、TTS、ASR、VLM、OCR。
 * 旧值 CHAT 仅用于读取历史数据时的别名兼容，不再允许新写入；
 * 读取时通过 {@link #canonical()} 将 CHAT 归一为 LLM。</p>
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
     * 判断当前类型是否允许写入新档案。
     *
     * @return {@code true} 表示可写入；历史兼容值 CHAT 返回 {@code false}
     */
    public boolean isWritable() {
        return this != CHAT;
    }

    /**
     * 取得用于业务判断的规范类型。
     *
     * @return CHAT 归一为 LLM，其他类型返回自身
     */
    public ModelType canonical() {
        return this == CHAT ? LLM : this;
    }

    /**
     * 判断当前类型与目标类型在历史别名归一后是否一致。
     *
     * @param target 目标模型类型
     * @return 是否表达同一种模型能力
     */
    public boolean matches(ModelType target) {
        return target != null && canonical() == target.canonical();
    }

    /**
     * 返回 7 个有效模型类型代码列表（不含旧值 CHAT）。
     *
     * @return 白名单列表
     */
    public static List<String> whitelist() {
        return Arrays.stream(values())
                .filter(ModelType::isWritable)
                .map(ModelType::getValue)
                .toList();
    }
}
