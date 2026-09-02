package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.utils.ParsedDocumentValidator;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;

/**
 * 解析完成后供分块、预览和发布复用的正式文档制品。
 *
 * <p>解析成功后写入 MinIO。对象只表达解析出的文档内容和必要来源信息，
 * 不混入任务、存储编排或对外 HTTP 信息。</p>
 *
 * <h3>顶层结构</h3>
 * <ul>
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
 * @author hongqy
 */
@Getter
@Setter
@Accessors(fluent = true)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ParsedDocument {

    /**
     * 文档业务标识。
     */
    private String documentKey;
    /**
     * 当前解析结果序号，从 1 递增。
     */
    private int resultVersion;
    /**
     * 源文件安全指针。
     */
    private ParsedDocumentSource source;
    /**
     * 实际执行的解析器信息。
     */
    private ParsedParserInfo parser;
    /**
     * 从内容中提取出的文档元数据。
     */
    private ParsedDocumentMetadata metadata;
    /**
     * 按文档顺序排列的内容块。
     */
    private List<ParsedBlock> blocks = new ArrayList<>();
    /**
     * 解析过程中的非致命警告。
     */
    private List<String> warnings = new ArrayList<>();
    /**
     * 安全化的解析执行轨迹。
     */
    private ParseTrace trace;
}
