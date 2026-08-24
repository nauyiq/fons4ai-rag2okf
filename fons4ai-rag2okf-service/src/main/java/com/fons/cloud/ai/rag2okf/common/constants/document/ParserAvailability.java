package com.fons.cloud.ai.rag2okf.common.constants.document;

/**
 * 解析器可用性状态。
 *
 * <p>{@code DocumentParser.availability()} 返回此值，{@code DocumentParserRegistry}
 * 据此判断是否允许进入实际解析链路。{@link #DISABLED} 仅保留扩展契约，
 * 注册表必须在返回解析器前抛出 {@code PARSER_NOT_AVAILABLE}，禁止回退到其他解析器。</p>
 *
 * @author hongqy
 */
public enum ParserAvailability {
    /** 已开放实际解析，注册表可直接返回该解析器。 */
    ENABLED,
    /** 仅保留扩展契约，未开放实际解析；请求该解析器必须失败，不回退。 */
    DISABLED
}
