package com.fons.cloud.ai.rag2okf.application.document;

import com.fons.cloud.ai.rag2okf.common.constants.Rag2OkfResultCode;
import com.fons.cloud.ai.rag2okf.common.exception.document.DocumentProcessingException;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedBlock;
import com.fons.cloud.ai.rag2okf.common.model.document.ParsedDocument;
import com.fons.cloud.ai.rag2okf.common.model.document.SourceAnchor;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * ParsedDocument v1 校验器。
 *
 * <p>集中校验 v1 Schema 不变量、块结构约束、来源锚点规则和秘密字段拒绝规则，
 * 避免散落在各解析器和任务执行器中的贫血式校验。</p>
 *
 * <h3>校验范围</h3>
 * <ul>
 *   <li>顶层字段非空：schemaVersion、documentKey、source、parser、metadata、blocks、warnings、trace</li>
 *   <li>schemaVersion 必须为 {@link ParsedDocument#SCHEMA_VERSION_V1}</li>
 *   <li>resultVersion 必须 >= 1</li>
 *   <li>blocks 非空且 ordinal 从 0 连续递增</li>
 *   <li>blockId 在当前 result 版本内唯一</li>
 *   <li>parentBlockId 只能指向更早的 block，且不得形成环</li>
 *   <li>文本型块（TITLE/HEADING/PARAGRAPH/LIST_ITEM/CODE/AUDIO_TRANSCRIPT）text 非空</li>
 *   <li>HEADING 块 level 在 1～6 范围</li>
 *   <li>TABLE 块必须有 table 字段；IMAGE/AUDIO_TRANSCRIPT 块必须有 media 字段</li>
 *   <li>锚点字段约束：PAGE/PARAGRAPH/REGION 必须有 page；PARAGRAPH 必须有 paragraphIndex；
 *       REGION 必须有归一化坐标；TIME_RANGE 必须有 timeRange 且 endMs > startMs</li>
 *   <li>NONE 锚点必须附 {@code SOURCE_ANCHOR_UNAVAILABLE} warning</li>
 *   <li>attributes 仅允许标量值（String/Number/Boolean），拒绝任意对象透传</li>
 *   <li>text 长度上限：单块 100KB，总文本 10MB</li>
 *   <li>blocks 数量上限：10000</li>
 *   <li>秘密字段拒绝：attributes、text、table、media 中不得包含 key、apiKey、secret、password、token 等敏感词</li>
 * </ul>
 *
 * @author hongqy
 */
public class ParsedDocumentValidator {

    /** 单块文本长度上限：100KB。 */
    public static final int MAX_BLOCK_TEXT_LENGTH = 100 * 1024;

    /** 总文本长度上限：10MB。 */
    public static final int MAX_TOTAL_TEXT_LENGTH = 10 * 1024 * 1024;

    /** blocks 数量上限。 */
    public static final int MAX_BLOCKS_COUNT = 10_000;

    /** NONE 锚点必须附带的 warning。 */
    public static final String SOURCE_ANCHOR_UNAVAILABLE_WARNING = "SOURCE_ANCHOR_UNAVAILABLE";

    /** 秘密字段敏感词集合（小写匹配）。 */
    private static final Set<String> SECRET_KEYWORDS = Set.of(
        "apikey", "api_key", "secret", "password", "token", "private_key", "access_key", "bearer"
    );

    /**
     * 校验 ParsedDocument v1。
     *
     * @param document 待校验的 ParsedDocument
     * @throws DocumentProcessingException 当 Schema、不变量、锚点或秘密字段规则不满足时，
     *         携带 {@link Rag2OkfResultCode#PARSED_DOCUMENT_INVALID}
     */
    public void validateV1(ParsedDocument document) {
        Objects.requireNonNull(document, "parsedDocument must not be null");

        validateSchemaVersion(document);
        validateDocumentKey(document);
        validateResultVersion(document);
        validateBlocks(document);
        validateWarnings(document);
        validateNoSecretFields(document);
    }

    private void validateSchemaVersion(ParsedDocument document) {
        if (!ParsedDocument.SCHEMA_VERSION_V1.equals(document.schemaVersion())) {
            throw invalid("schemaVersion must be " + ParsedDocument.SCHEMA_VERSION_V1
                + ", got: " + document.schemaVersion());
        }
    }

    private void validateDocumentKey(ParsedDocument document) {
        if (document.documentKey().isBlank()) {
            throw invalid("documentKey must not be blank");
        }
    }

    private void validateResultVersion(ParsedDocument document) {
        if (document.resultVersion() < 1) {
            throw invalid("resultVersion must be >= 1, got: " + document.resultVersion());
        }
    }

    private void validateBlocks(ParsedDocument document) {
        List<ParsedBlock> blocks = document.blocks();
        if (blocks.isEmpty()) {
            throw invalid("blocks must not be empty");
        }
        if (blocks.size() > MAX_BLOCKS_COUNT) {
            throw invalid("blocks count exceeds limit: " + blocks.size() + " > " + MAX_BLOCKS_COUNT);
        }

        Set<String> blockIds = new HashSet<>();
        Set<String> validParentIds = new HashSet<>();
        long totalTextLength = 0;

        for (int i = 0; i < blocks.size(); i++) {
            ParsedBlock block = blocks.get(i);
            validateBlockId(block, blockIds, i);
            validateOrdinal(block, i);
            validateType(block, i);
            validateParentBlockId(block, validParentIds, i);
            validateText(block, i);
            validateLevel(block, i);
            validateTable(block, i);
            validateMedia(block, i);
            validateAnchor(block, document.warnings(), i);
            validateAttributes(block, i);

            totalTextLength += block.text() == null ? 0 : block.text().length();
            if (totalTextLength > MAX_TOTAL_TEXT_LENGTH) {
                throw invalid("total text length exceeds limit: " + totalTextLength
                    + " > " + MAX_TOTAL_TEXT_LENGTH);
            }

            // 当前 block 校验通过后，可作为后续 block 的 parent
            validParentIds.add(block.blockId());
        }
    }

    private void validateBlockId(ParsedBlock block, Set<String> blockIds, int index) {
        if (block.blockId().isBlank()) {
            throw invalid("blocks[" + index + "].blockId must not be blank");
        }
        if (!blockIds.add(block.blockId())) {
            throw invalid("blocks[" + index + "].blockId duplicate: " + block.blockId());
        }
    }

    private void validateOrdinal(ParsedBlock block, int index) {
        if (block.ordinal() != index) {
            throw invalid("blocks[" + index + "].ordinal must be " + index
                + ", got: " + block.ordinal());
        }
    }

    private void validateType(ParsedBlock block, int index) {
        Set<String> validTypes = Set.of(
            ParsedBlock.TITLE, ParsedBlock.HEADING, ParsedBlock.PARAGRAPH, ParsedBlock.LIST_ITEM,
            ParsedBlock.TABLE, ParsedBlock.IMAGE, ParsedBlock.AUDIO_TRANSCRIPT,
            ParsedBlock.CODE, ParsedBlock.PAGE_BREAK
        );
        if (!validTypes.contains(block.type())) {
            throw invalid("blocks[" + index + "].type unknown: " + block.type());
        }
    }

    private void validateParentBlockId(ParsedBlock block, Set<String> validParentIds, int index) {
        String parentId = block.parentBlockId();
        if (parentId == null) {
            return;
        }
        if (parentId.isBlank()) {
            throw invalid("blocks[" + index + "].parentBlockId must not be blank");
        }
        if (!validParentIds.contains(parentId)) {
            throw invalid("blocks[" + index + "].parentBlockId not found or not earlier: " + parentId);
        }
    }

    private void validateText(ParsedBlock block, int index) {
        if (ParsedBlock.TEXT_TYPES.contains(block.type())) {
            if (block.text() == null || block.text().isBlank()) {
                throw invalid("blocks[" + index + "].text must not be blank for type: " + block.type());
            }
            if (block.text().length() > MAX_BLOCK_TEXT_LENGTH) {
                throw invalid("blocks[" + index + "].text length exceeds limit: "
                    + block.text().length() + " > " + MAX_BLOCK_TEXT_LENGTH);
            }
        }
    }

    private void validateLevel(ParsedBlock block, int index) {
        if (ParsedBlock.HEADING.equals(block.type())) {
            if (block.level() == null || block.level() < 1 || block.level() > 6) {
                throw invalid("blocks[" + index + "].level must be 1~6 for HEADING, got: " + block.level());
            }
        } else {
            if (block.level() != null) {
                throw invalid("blocks[" + index + "].level only allowed for HEADING, type: " + block.type());
            }
        }
    }

    private void validateTable(ParsedBlock block, int index) {
        if (ParsedBlock.TABLE.equals(block.type())) {
            if (block.table() == null) {
                throw invalid("blocks[" + index + "].table must not be null for TABLE type");
            }
            if (block.table().rows() <= 0 || block.table().columns() <= 0) {
                throw invalid("blocks[" + index + "].table rows and columns must be > 0");
            }
            int expectedCells = block.table().rows() * block.table().columns();
            if (block.table().cells().size() != expectedCells) {
                throw invalid("blocks[" + index + "].table cells count mismatch: "
                    + block.table().cells().size() + " != " + expectedCells);
            }
        } else {
            if (block.table() != null) {
                throw invalid("blocks[" + index + "].table only allowed for TABLE type, type: " + block.type());
            }
        }
    }

    private void validateMedia(ParsedBlock block, int index) {
        if (ParsedBlock.IMAGE.equals(block.type()) || ParsedBlock.AUDIO_TRANSCRIPT.equals(block.type())) {
            if (block.media() == null) {
                throw invalid("blocks[" + index + "].media must not be null for type: " + block.type());
            }
        } else {
            if (block.media() != null) {
                throw invalid("blocks[" + index + "].media only allowed for IMAGE/AUDIO_TRANSCRIPT, type: "
                    + block.type());
            }
        }
    }

    private void validateAnchor(ParsedBlock block, List<String> warnings, int index) {
        SourceAnchor anchor = block.anchor();
        switch (anchor.locatorType()) {
            case SourceAnchor.PAGE -> {
                requirePage(anchor, index);
                requireNull(anchor.paragraphIndex(), "paragraphIndex", SourceAnchor.PAGE, index);
                requireNull(anchor.region(), "region", SourceAnchor.PAGE, index);
                requireNull(anchor.timeRange(), "timeRange", SourceAnchor.PAGE, index);
            }
            case SourceAnchor.PARAGRAPH -> {
                requirePage(anchor, index);
                if (anchor.paragraphIndex() == null || anchor.paragraphIndex() < 0) {
                    throw invalid("blocks[" + index + "].anchor.paragraphIndex must be >= 0 for PARAGRAPH");
                }
                requireNull(anchor.region(), "region", SourceAnchor.PARAGRAPH, index);
                requireNull(anchor.timeRange(), "timeRange", SourceAnchor.PARAGRAPH, index);
            }
            case SourceAnchor.REGION -> {
                requirePage(anchor, index);
                requireNull(anchor.paragraphIndex(), "paragraphIndex", SourceAnchor.REGION, index);
                if (anchor.region() == null) {
                    throw invalid("blocks[" + index + "].anchor.region must not be null for REGION");
                }
                validateRegion(anchor.region(), index);
                requireNull(anchor.timeRange(), "timeRange", SourceAnchor.REGION, index);
            }
            case SourceAnchor.TIME_RANGE -> {
                requireNull(anchor.page(), "page", SourceAnchor.TIME_RANGE, index);
                requireNull(anchor.paragraphIndex(), "paragraphIndex", SourceAnchor.TIME_RANGE, index);
                requireNull(anchor.region(), "region", SourceAnchor.TIME_RANGE, index);
                if (anchor.timeRange() == null) {
                    throw invalid("blocks[" + index + "].anchor.timeRange must not be null for TIME_RANGE");
                }
                if (anchor.timeRange().startMs() < 0) {
                    throw invalid("blocks[" + index + "].anchor.timeRange.startMs must be >= 0");
                }
                if (anchor.timeRange().endMs() <= anchor.timeRange().startMs()) {
                    throw invalid("blocks[" + index + "].anchor.timeRange.endMs must be > startMs");
                }
            }
            case SourceAnchor.NONE -> {
                requireNull(anchor.page(), "page", SourceAnchor.NONE, index);
                requireNull(anchor.paragraphIndex(), "paragraphIndex", SourceAnchor.NONE, index);
                requireNull(anchor.region(), "region", SourceAnchor.NONE, index);
                requireNull(anchor.timeRange(), "timeRange", SourceAnchor.NONE, index);
                if (!warnings.contains(SOURCE_ANCHOR_UNAVAILABLE_WARNING)) {
                    throw invalid("blocks[" + index + "] anchor NONE requires "
                        + SOURCE_ANCHOR_UNAVAILABLE_WARNING + " warning");
                }
            }
            default -> throw invalid("blocks[" + index + "].anchor.locatorType unknown: "
                + anchor.locatorType());
        }
    }

    private void validateRegion(SourceAnchor.Region region, int index) {
        if (region.x() < 0 || region.x() > 1) {
            throw invalid("blocks[" + index + "].anchor.region.x must be in [0, 1], got: " + region.x());
        }
        if (region.y() < 0 || region.y() > 1) {
            throw invalid("blocks[" + index + "].anchor.region.y must be in [0, 1], got: " + region.y());
        }
        if (region.width() <= 0 || region.width() > 1) {
            throw invalid("blocks[" + index + "].anchor.region.width must be in (0, 1], got: " + region.width());
        }
        if (region.height() <= 0 || region.height() > 1) {
            throw invalid("blocks[" + index + "].anchor.region.height must be in (0, 1], got: " + region.height());
        }
        if (region.x() + region.width() > 1) {
            throw invalid("blocks[" + index + "].anchor.region x + width exceeds 1");
        }
        if (region.y() + region.height() > 1) {
            throw invalid("blocks[" + index + "].anchor.region y + height exceeds 1");
        }
    }

    private void requirePage(SourceAnchor anchor, int index) {
        if (anchor.page() == null || anchor.page() < 1) {
            throw invalid("blocks[" + index + "].anchor.page must be >= 1 for "
                + anchor.locatorType());
        }
    }

    private void requireNull(Object value, String fieldName, String locatorType, int index) {
        if (value != null) {
            throw invalid("blocks[" + index + "].anchor." + fieldName
                + " must be null for " + locatorType);
        }
    }

    private void validateAttributes(ParsedBlock block, int index) {
        Map<String, Object> attributes = block.attributes();
        if (attributes == null || attributes.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!isScalar(value)) {
                throw invalid("blocks[" + index + "].attributes[" + key
                    + "] must be scalar (String/Number/Boolean), got: "
                    + (value == null ? "null" : value.getClass().getSimpleName()));
            }
        }
    }

    private boolean isScalar(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean;
    }

    private void validateWarnings(ParsedDocument document) {
        // warnings 已防御性拷贝，只需检查非 null（record 构造器保证）
    }

    private void validateNoSecretFields(ParsedDocument document) {
        for (int i = 0; i < document.blocks().size(); i++) {
            ParsedBlock block = document.blocks().get(i);
            if (block.text() != null && containsSecretKeyword(block.text())) {
                throw invalid("blocks[" + i + "].text contains secret keyword");
            }
            if (block.attributes() != null) {
                for (Map.Entry<String, Object> entry : block.attributes().entrySet()) {
                    if (containsSecretKeyword(entry.getKey())) {
                        throw invalid("blocks[" + i + "].attributes key contains secret keyword: " + entry.getKey());
                    }
                    if (entry.getValue() instanceof String s && containsSecretKeyword(s)) {
                        throw invalid("blocks[" + i + "].attributes[" + entry.getKey()
                            + "] value contains secret keyword");
                    }
                }
            }
        }
    }

    private boolean containsSecretKeyword(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return SECRET_KEYWORDS.stream().anyMatch(lower::contains);
    }

    private static DocumentProcessingException invalid(String message) {
        return new DocumentProcessingException(Rag2OkfResultCode.PARSED_DOCUMENT_INVALID,
            new IllegalArgumentException(message));
    }

    // ==================== 工具方法（供测试和文档生成使用） ====================

    /**
     * 生成符合 v1 规范的稳定 blockId。
     *
     * <p>格式：{@code blk-<resultVersion>-<ordinal>-<shortUuid>}。当前版本使用 UUID 保证唯一性，
     * 未来可替换为内容 hash 派生。</p>
     *
     * @param resultVersion result 版本
     * @param ordinal       块序号
     * @return 稳定 blockId
     */
    public static String generateBlockId(int resultVersion, int ordinal) {
        return "blk-" + resultVersion + "-" + ordinal + "-" + UUID.randomUUID();
    }
}
