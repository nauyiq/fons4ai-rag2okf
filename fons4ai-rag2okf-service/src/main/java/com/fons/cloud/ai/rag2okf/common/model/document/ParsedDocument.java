package com.fons.cloud.ai.rag2okf.common.model.document;

import java.util.List;
import java.util.Objects;

/**
 * ParsedDocument v1。
 *
 * <p>解析成功的不可变事实，写入 MinIO 后供分块、预览和发布复用。v1 Schema 不可变，
 * 演进必须新增版本（v1.1/v2），不得修改已写入的 v1 成功制品。</p>
 *
 * <h3>顶层结构</h3>
 * <ul>
 *   <li>{@code schemaVersion} — 固定 {@link #SCHEMA_VERSION_V1}，即 {@code "1.0"}</li>
 *   <li>{@code documentKey} — 文档业务键</li>
 *   <li>{@code resultVersion} — result 版本，从 1 递增</li>
 *   <li>{@code source} — 源文件指针</li>
 *   <li>{@code parser} — 解析器元数据</li>
 *   <li>{@code metadata} — 文档元数据</li>
 *   <li>{@code blocks} — 扁平有序块列表</li>
 *   <li>{@code warnings} — 解析警告列表</li>
 *   <li>{@code trace} — 解析追踪步骤</li>
 * </ul>
 *
 * <p>本记录只表达文档事实，不混入任务和存储编排。字段不变量由
 * {@link ParsedDocumentValidator} 集中校验，避免贫血式散落校验。</p>
 *
 * @param schemaVersion Schema 版本，固定 {@code "1.0"}
 * @param documentKey   文档业务键
 * @param resultVersion result 版本，从 1 递增
 * @param source        源文件指针
 * @param parser        解析器元数据
 * @param metadata      文档元数据
 * @param blocks        扁平有序块列表
 * @param warnings      解析警告列表
 * @param trace         解析追踪步骤
 * @author hongqy
 */
public record ParsedDocument(
    String schemaVersion,
    String documentKey,
    int resultVersion,
    Source source,
    ParserInfo parser,
    Metadata metadata,
    List<ParsedBlock> blocks,
    List<String> warnings,
    Trace trace
) {

    /** v1 Schema 版本标识。 */
    public static final String SCHEMA_VERSION_V1 = "1.0";

    /** 紧凑构造器，防御性拷贝列表并拒绝空值。字段约束由 {@link ParsedDocumentValidator} 统一校验。 */
    public ParsedDocument {
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(documentKey, "documentKey must not be null");
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(parser, "parser must not be null");
        Objects.requireNonNull(metadata, "metadata must not be null");
        Objects.requireNonNull(blocks, "blocks must not be null");
        Objects.requireNonNull(warnings, "warnings must not be null");
        Objects.requireNonNull(trace, "trace must not be null");
        blocks = List.copyOf(blocks);
        warnings = List.copyOf(warnings);
    }

    /**
     * 源文件指针。
     *
     * @param fileToken   MinIO 源文件 token
     * @param filename    原始文件名
     * @param contentType MIME 类型
     * @param sha256      SHA-256 摘要
     */
    public record Source(String fileToken, String filename, String contentType, String sha256) {
        public Source {
            Objects.requireNonNull(fileToken, "fileToken must not be null");
            Objects.requireNonNull(filename, "filename must not be null");
            Objects.requireNonNull(contentType, "contentType must not be null");
            Objects.requireNonNull(sha256, "sha256 must not be null");
        }
    }

    /**
     * 解析器元数据。
     *
     * @param type            解析器类型，如 {@code BUILT_IN}
     * @param workflowVersion 工作流版本，如 {@code builtin-v1}
     */
    public record ParserInfo(String type, String workflowVersion) {
        public ParserInfo {
            Objects.requireNonNull(type, "parser type must not be null");
            Objects.requireNonNull(workflowVersion, "workflowVersion must not be null");
        }
    }

    /**
     * 文档元数据。
     *
     * @param title       文档标题，可空
     * @param language    语言代码，如 {@code zh-CN}
     * @param pageCount   页数，可空
     * @param durationMs  音视频时长（毫秒），可空
     */
    public record Metadata(String title, String language, Integer pageCount, Long durationMs) {
    }

    /**
     * 解析追踪信息。
     *
     * <p>只记录 step、capability、profileKey、状态、耗时和外部 traceId，
     * 不记录 prompt、全文响应或凭证。</p>
     *
     * @param steps 追踪步骤列表
     */
    public record Trace(List<TraceStep> steps) {
        public Trace {
            Objects.requireNonNull(steps, "steps must not be null");
            steps = List.copyOf(steps);
        }

        /** 创建空追踪。 */
        public static Trace empty() {
            return new Trace(List.of());
        }
    }

    /**
     * 追踪步骤。
     *
     * @param step        步骤名称
     * @param capability  模型能力，如 {@code LLM}；纯本地步骤为 null
     * @param profileKey  模型档案 key；纯本地步骤为 null
     * @param status      状态：{@code SUCCESS}、{@code WARNING}、{@code FAILED}
     * @param durationMs  耗时（毫秒）
     * @param externalTraceId 外部 traceId，用于关联模型调用日志
     */
    public record TraceStep(
        String step,
        String capability,
        String profileKey,
        String status,
        long durationMs,
        String externalTraceId
    ) {
        public TraceStep {
            Objects.requireNonNull(step, "step must not be null");
            Objects.requireNonNull(status, "status must not be null");
        }
    }
}
