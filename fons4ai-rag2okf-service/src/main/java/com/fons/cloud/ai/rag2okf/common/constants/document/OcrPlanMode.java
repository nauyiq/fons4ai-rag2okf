package com.fons.cloud.ai.rag2okf.common.constants.document;

/**
 * OCR 计划的执行范围。
 *
 * <p>该枚举只描述原生解析结果是否足以作为后续解析原料；不表示 OCR Provider、
 * 调用次数或计费方式。</p>
 *
 * @author hongqy
 */
public enum OcrPlanMode {

    /** 原生文本可用，不需要 OCR。 */
    NONE,

    /** 仅指定页的原生文本不足，需要 OCR。 */
    PAGE_SELECTIVE,

    /** 全部页面或整个单页媒体需要 OCR。 */
    REQUIRED
}
