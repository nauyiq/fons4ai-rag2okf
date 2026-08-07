package com.fons.cloud.ai.rag2okf.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.Conflicts;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.Alias;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.IndexSettings;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 基于 Fons4Cloud 官方 Elasticsearch 客户端的发布投影适配器（技术设计 §4.9、§5.6）。
 *
 * <p>实现 {@link PublicationProjectionPort}，只使用官方 {@link ElasticsearchClient}，
 * 不引入 Easy-ES 或第二套检索框架（D-007）。
 *
 * <p>设计约束：
 * <ul>
 *   <li>物理索引：{@code kb-chunk-v1}；读写别名：{@code kb-chunk-read}、{@code kb-chunk-write}。</li>
 *   <li>V1 含 {@code dense_vector} 字段，dims 从 {@code sys.embedding.dims} 注入（D-007，CR-013 调整）。</li>
 *   <li>bulk 写入任一失败整体标记 FAILED（§5.6 第 4 步）。</li>
 *   <li>清理旧投影通过 {@code delete_by_query} 按 publicationRevisionKey 过滤（§5.6 第 7 步）。</li>
 *   <li>schemaVersion 固定为 {@code v1}，便于后续索引迁移。</li>
 * </ul>
 *
 * <p>本适配器不负责授权、CAS 指针切换或 Outbox 触发。
 *
 * @author hongqy
 */
@Slf4j
@Component
public class ElasticsearchPublicationProjection implements PublicationProjectionPort {

    /** 物理索引名。 */
    public static final String PHYSICAL_INDEX = "kb-chunk-v1";
    /** 读别名。 */
    public static final String READ_ALIAS = "kb-chunk-read";
    /** 写别名。 */
    public static final String WRITE_ALIAS = "kb-chunk-write";
    /** Schema 版本。 */
    public static final String SCHEMA_VERSION = "v1";
    /** Mapping 资源路径。 */
    public static final String MAPPING_RESOURCE = "elasticsearch/kb-chunk-v1-mapping.json";

    /** 错误码：写入失败。 */
    public static final String ERR_PROJECTION_WRITE = "PROJECTION_WRITE_ERROR";
    /** 错误码：清理失败。 */
    public static final String ERR_PROJECTION_CLEANUP = "PROJECTION_CLEANUP_ERROR";
    /** 错误码：查询失败。 */
    public static final String ERR_PROJECTION_QUERY = "PROJECTION_QUERY_ERROR";
    /** 错误码：索引启动失败。 */
    public static final String ERR_PROJECTION_BOOTSTRAP = "PROJECTION_BOOTSTRAP_ERROR";
    /** 错误码：校验失败。 */
    public static final String ERR_PROJECTION_VERIFY = "PROJECTION_VERIFY_ERROR";

    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper;
    private final int embeddingDims;

    public ElasticsearchPublicationProjection(
            ElasticsearchClient client,
            ObjectMapper objectMapper,
            @Value("${sys.embedding.dims:1024}") int embeddingDims) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.embeddingDims = embeddingDims;
    }

    @Override
    public void bootstrapIndex() {
        try {
            if (indexExists(PHYSICAL_INDEX)) {
                verifyExistingIndexDims();
                log.debug("Elasticsearch index already exists: {}, dims={}",
                        PHYSICAL_INDEX, embeddingDims);
                return;
            }
            createIndexWithAliases();
            log.info("Elasticsearch index bootstrapped: {}, aliases=[{}, {}], dims={}",
                    PHYSICAL_INDEX, READ_ALIAS, WRITE_ALIAS, embeddingDims);
        } catch (IOException e) {
            throw new ProjectionException(ERR_PROJECTION_BOOTSTRAP,
                    "Failed to bootstrap Elasticsearch index: " + PHYSICAL_INDEX, e);
        }
    }

    /**
     * 校验已存在索引的 {@code vector.dims} 与系统配置 {@code sys.embedding.dims} 一致
     * （T042 Verification：不一致启动失败）。
     *
     * <p>CR-013 单维度约束：所有写入向量的 chunk 必须使用同一维度，否则 dense_vector
     * 查询和 kNN 检索会失败。索引已存在但 dims 不匹配时抛出 {@link ProjectionException}，
     * 阻止服务启动。
     */
    private void verifyExistingIndexDims() throws IOException {
        GetMappingRequest request = GetMappingRequest.of(g -> g.index(PHYSICAL_INDEX));
        var response = client.indices().getMapping(request);
        var record = response.get(PHYSICAL_INDEX);
        if (record == null || record.mappings() == null
                || record.mappings().properties() == null) {
            throw new ProjectionException(ERR_PROJECTION_BOOTSTRAP,
                    "Existing index missing mapping: " + PHYSICAL_INDEX);
        }
        var vectorProps = record.mappings().properties().get("vector");
        if (vectorProps == null || !vectorProps.isDenseVector()) {
            throw new ProjectionException(ERR_PROJECTION_BOOTSTRAP,
                    "Existing index missing vector dense_vector mapping: " + PHYSICAL_INDEX);
        }
        Integer actualDims = vectorProps.denseVector().dims();
        if (actualDims == null || actualDims != embeddingDims) {
            throw new ProjectionException(ERR_PROJECTION_BOOTSTRAP,
                    "Existing index vector dims mismatch: expected=" + embeddingDims
                            + ", actual=" + actualDims + ", index=" + PHYSICAL_INDEX);
        }
    }

    @Override
    public ProjectionResult projectChunks(ProjectionRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.chunks() == null || request.chunks().isEmpty()) {
            throw new ProjectionException(ERR_PROJECTION_VERIFY,
                    "Empty chunk projection rejected: publicationRevisionKey=" + request.publicationRevisionKey());
        }

        BulkRequest bulk = buildBulkRequest(request);
        try {
            BulkResponse response = client.bulk(bulk);
            if (response.errors()) {
                String firstError = extractFirstError(response.items());
                log.error("Bulk projection had failures: publicationRevisionKey={}, errors={}",
                        request.publicationRevisionKey(), firstError);
                throw new ProjectionException(ERR_PROJECTION_WRITE,
                        "Bulk projection had failures: " + firstError);
            }

            int written = response.items().size();
            log.info("Projection written: publicationRevisionKey={}, count={}",
                    request.publicationRevisionKey(), written);
            return new ProjectionResult(PHYSICAL_INDEX, written, request.contentHash());

        } catch (IOException e) {
            throw new ProjectionException(ERR_PROJECTION_WRITE,
                    "Failed to bulk project chunks: " + e.getMessage(), e);
        }
    }

    @Override
    public long deleteByPublicationRevisionKey(String publicationRevisionKey) {
        Objects.requireNonNull(publicationRevisionKey, "publicationRevisionKey");
        try {
            DeleteByQueryRequest request = DeleteByQueryRequest.of(d -> d
                    .index(READ_ALIAS)
                    .query(buildPublicationRevisionQuery(publicationRevisionKey))
                    .refresh(true)
                    .conflicts(Conflicts.Proceed));
            DeleteByQueryResponse response = client.deleteByQuery(request);
            long deleted = response.deleted() == null ? 0L : response.deleted();
            log.info("Projection cleanup: publicationRevisionKey={}, deleted={}",
                    publicationRevisionKey, deleted);
            return deleted;
        } catch (IOException e) {
            throw new ProjectionException(ERR_PROJECTION_CLEANUP,
                    "Failed to delete projection: " + e.getMessage(), e);
        }
    }

    @Override
    public long countByPublicationRevisionKey(String publicationRevisionKey) {
        Objects.requireNonNull(publicationRevisionKey, "publicationRevisionKey");
        try {
            CountRequest request = CountRequest.of(c -> c
                    .index(READ_ALIAS)
                    .query(buildPublicationRevisionQuery(publicationRevisionKey)));
            CountResponse response = client.count(request);
            return response.count();
        } catch (IOException e) {
            throw new ProjectionException(ERR_PROJECTION_QUERY,
                    "Failed to count projection: " + e.getMessage(), e);
        }
    }

    // ────────────────────────────── 内部辅助 ──────────────────────────────

    private boolean indexExists(String index) throws IOException {
        return client.indices().exists(ExistsRequest.of(e -> e.index(index))).value();
    }

    private void createIndexWithAliases() throws IOException {
        Map<String, Alias> aliases = new LinkedHashMap<>();
        aliases.put(READ_ALIAS, Alias.of(al -> al.isWriteIndex(false)));
        aliases.put(WRITE_ALIAS, Alias.of(al -> al.isWriteIndex(true)));

        IndexSettings settings = IndexSettings.of(s -> s
                .numberOfShards("1")
                .numberOfReplicas("0"));

        TypeMapping mapping = readMapping();

        CreateIndexRequest request = CreateIndexRequest.of(c -> c
                .index(PHYSICAL_INDEX)
                .settings(settings)
                .mappings(mapping)
                .aliases(aliases));

        client.indices().create(request);
    }

    /**
     * 从 classpath JSON 资源读取 mapping，并将 {@code vector.dims} 注入为配置值
     * {@code sys.embedding.dims}（D-007，CR-013 调整）。
     *
     * <p>JSON 只描述 properties/_source/dynamic，dims 占位值由配置覆盖，
     * 保证新建索引的 dims 与系统配置一致。
     */
    private TypeMapping readMapping() throws IOException {
        try (InputStream in = new ClassPathResource(MAPPING_RESOURCE).getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            JsonNode vectorNode = root.path("mappings").path("properties").path("vector");
            if (!vectorNode.isMissingNode() && vectorNode.has("dims")) {
                ((ObjectNode) vectorNode).put("dims", embeddingDims);
            }
            byte[] json = objectMapper.writeValueAsBytes(root);
            try (InputStream jsonIn = new java.io.ByteArrayInputStream(json)) {
                return TypeMapping.of(tm -> tm.withJson(jsonIn));
            }
        }
    }

    /**
     * 暴露 ObjectMapper，仅供单元测试在断言 mapping 时使用。
     */
    ObjectMapper objectMapper() {
        return objectMapper;
    }

    private BulkRequest buildBulkRequest(ProjectionRequest request) {
        List<BulkOperation> operations = new ArrayList<>(request.chunks().size());
        for (ChunkProjection chunk : request.chunks()) {
            Map<String, Object> doc = buildDocument(request, chunk);
            operations.add(BulkOperation.of(op -> op.index(i -> i
                    .index(WRITE_ALIAS)
                    .id(chunk.chunkKey())
                    .document(doc))));
        }
        return BulkRequest.of(b -> b.operations(operations).refresh(Refresh.True));
    }

    private Map<String, Object> buildDocument(ProjectionRequest request, ChunkProjection chunk) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("chunkKey", chunk.chunkKey());
        doc.put("workspaceKey", request.workspaceKey());
        doc.put("knowledgeBaseKey", request.knowledgeBaseKey());
        doc.put("documentKey", request.documentKey());
        doc.put("publicationRevisionKey", request.publicationRevisionKey());
        doc.put("parseRevisionKey", request.parseRevisionKey());
        doc.put("chunkRevisionKey", request.chunkRevisionKey());
        doc.put("parentChunkKey", chunk.parentChunkKey());
        doc.put("chunkLevel", chunk.chunkLevel());
        doc.put("ordinal", chunk.ordinal());
        doc.put("rawText", chunk.rawText());
        doc.put("displayText", chunk.displayText());
        doc.put("embeddingText", chunk.embeddingText());
        doc.put("titlePath", chunk.titlePath());
        doc.put("titlePathText", chunk.titlePath());
        doc.put("sourceLocatorType", chunk.sourceLocatorType());
        doc.put("pageNumber", chunk.pageNumber());
        doc.put("startOffset", chunk.startOffset());
        doc.put("endOffset", chunk.endOffset());
        doc.put("contentHash", request.contentHash());
        doc.put("schemaVersion", SCHEMA_VERSION);
        // CR-013：写入向量字段（D-007 dense_vector）；skipEmbedding 或无 EMBEDDING 绑定时为 null
        if (chunk.vector() != null) {
            doc.put("vector", chunk.vector());
        }
        return doc;
    }

    private Query buildPublicationRevisionQuery(String publicationRevisionKey) {
        return Query.of(q -> q.term(t -> t
                .field("publicationRevisionKey")
                .value(v -> v.stringValue(publicationRevisionKey))));
    }

    private String extractFirstError(List<BulkResponseItem> items) {
        for (BulkResponseItem item : items) {
            if (item.error() != null) {
                return item.id() + ": " + item.error().reason();
            }
        }
        return "unknown";
    }

    /**
     * 返回支持的物理索引名，仅供测试与运维观察。
     */
    public String physicalIndex() {
        return PHYSICAL_INDEX;
    }

    /**
     * 返回读别名，仅供测试与运维观察。
     */
    public String readAlias() {
        return READ_ALIAS;
    }

    /**
     * 返回写别名，仅供测试与运维观察。
     */
    public String writeAlias() {
        return WRITE_ALIAS;
    }
}
