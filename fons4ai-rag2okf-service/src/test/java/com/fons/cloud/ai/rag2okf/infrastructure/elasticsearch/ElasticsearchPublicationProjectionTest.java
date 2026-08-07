package com.fons.cloud.ai.rag2okf.infrastructure.elasticsearch;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.DeleteByQueryRequest;
import co.elastic.clients.elasticsearch.core.DeleteByQueryResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ChunkProjection;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ProjectionException;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ProjectionRequest;
import com.fons.cloud.ai.rag2okf.common.dto.PublicationProjectionPort.ProjectionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ElasticsearchPublicationProjection 单元测试，覆盖 AC-016、AC-017、AC-018、AC-022、AC-024。
 *
 * <p>使用 Mockito 模拟 {@link ElasticsearchClient}，不依赖真实 ES 容器。
 * 真实容器集成测试归 T020/T022。
 *
 * @author hongqy
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ES 发布投影适配器")
@org.mockito.junit.jupiter.MockitoSettings(strictness = Strictness.LENIENT)
class ElasticsearchPublicationProjectionTest {

    @Mock private ElasticsearchClient client;
    @Mock private ElasticsearchIndicesClient indicesClient;

    private ElasticsearchPublicationProjection adapter;

    @BeforeEach
    void setUp() throws IOException {
        adapter = new ElasticsearchPublicationProjection(client, new ObjectMapper(), 1024);
        lenient().when(client.indices()).thenReturn(indicesClient);
        lenient().when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(true));
    }

    @Test
    @DisplayName("bootstrapIndex: 索引已存在且 dims 匹配时跳过创建")
    void shouldSkipBootstrapWhenIndexExists() throws IOException {
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(true));
        GetMappingResponse mappingResponse = buildMappingResponse(1024);
        when(indicesClient.getMapping(any(GetMappingRequest.class)))
                .thenReturn(mappingResponse);

        adapter.bootstrapIndex();

        // 不应调用 create
        verify(indicesClient).exists(any(ExistsRequest.class));
    }

    @Test
    @DisplayName("bootstrapIndex: 索引已存在但 dims 不匹配时启动失败（T042）")
    void shouldFailBootstrapWhenDimsMismatch() throws IOException {
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(true));
        GetMappingResponse mappingResponse = buildMappingResponse(768);
        when(indicesClient.getMapping(any(GetMappingRequest.class)))
                .thenReturn(mappingResponse);

        assertThatThrownBy(() -> adapter.bootstrapIndex())
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> assertThat(((ProjectionException) ex).errorCode())
                        .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_BOOTSTRAP));
    }

    @Test
    @DisplayName("bootstrapIndex: 索引已存在但缺少 vector 字段时启动失败（T042）")
    void shouldFailBootstrapWhenVectorMissing() throws IOException {
        when(indicesClient.exists(any(ExistsRequest.class)))
                .thenReturn(new BooleanResponse(true));
        GetMappingResponse mappingResponse = buildMappingResponseWithoutVector();
        when(indicesClient.getMapping(any(GetMappingRequest.class)))
                .thenReturn(mappingResponse);

        assertThatThrownBy(() -> adapter.bootstrapIndex())
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> assertThat(((ProjectionException) ex).errorCode())
                        .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_BOOTSTRAP));
    }

    @Test
    @DisplayName("projectChunks: 成功写入返回 ProjectionResult")
    void shouldProjectChunksSuccessfully() throws IOException {
        ProjectionRequest request = buildSampleRequest(2);
        BulkResponseItem item0 = bulkItem("chunk-0", false, null);
        BulkResponseItem item1 = bulkItem("chunk-1", false, null);
        BulkResponse bulkResponse = BulkResponse.of(b -> b
                .errors(false)
                .items(List.of(item0, item1))
                .took(10L));
        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        ProjectionResult result = adapter.projectChunks(request);

        assertThat(result.projectionIndex()).isEqualTo(ElasticsearchPublicationProjection.PHYSICAL_INDEX);
        assertThat(result.projectionCount()).isEqualTo(2);
        assertThat(result.contentHash()).isEqualTo(request.contentHash());

        ArgumentCaptor<BulkRequest> captor = ArgumentCaptor.forClass(BulkRequest.class);
        verify(client).bulk(captor.capture());
        assertThat(captor.getValue().operations()).hasSize(2);
    }

    @Test
    @DisplayName("projectChunks: bulk 部分失败时抛出 PROJECTION_WRITE_ERROR")
    void shouldFailWhenBulkHasErrors() throws IOException {
        ProjectionRequest request = buildSampleRequest(1);
        BulkResponseItem item = bulkItem("chunk-0", true, "version conflict");
        BulkResponse bulkResponse = BulkResponse.of(b -> b
                .errors(true)
                .items(List.of(item))
                .took(5L));
        when(client.bulk(any(BulkRequest.class))).thenReturn(bulkResponse);

        assertThatThrownBy(() -> adapter.projectChunks(request))
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> {
                    ProjectionException pe = (ProjectionException) ex;
                    assertThat(pe.errorCode())
                            .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_WRITE);
                });
    }

    @Test
    @DisplayName("projectChunks: 空分块列表拒绝写入")
    void shouldRejectEmptyChunkList() {
        ProjectionRequest request = new ProjectionRequest(
                "pub-01", "ws", "kb", "doc", "parse", "chunk",
                "hash", List.of());
        assertThatThrownBy(() -> adapter.projectChunks(request))
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> assertThat(((ProjectionException) ex).errorCode())
                        .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_VERIFY));
    }

    @Test
    @DisplayName("projectChunks: IO 异常包装为 PROJECTION_WRITE_ERROR")
    void shouldWrapIOExceptionOnBulk() throws IOException {
        ProjectionRequest request = buildSampleRequest(1);
        when(client.bulk(any(BulkRequest.class))).thenThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> adapter.projectChunks(request))
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> assertThat(((ProjectionException) ex).errorCode())
                        .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_WRITE));
    }

    @Test
    @DisplayName("deleteByPublicationRevisionKey: 返回实际删除数量")
    void shouldDeleteByPublicationRevisionKey() throws IOException {
        DeleteByQueryResponse response = mock(DeleteByQueryResponse.class);
        when(response.deleted()).thenReturn(5L);
        when(client.deleteByQuery(any(DeleteByQueryRequest.class))).thenReturn(response);

        long deleted = adapter.deleteByPublicationRevisionKey("pub-01");

        assertThat(deleted).isEqualTo(5L);
        verify(client).deleteByQuery(any(DeleteByQueryRequest.class));
    }

    @Test
    @DisplayName("deleteByPublicationRevisionKey: IO 异常包装为 PROJECTION_CLEANUP_ERROR")
    void shouldWrapIOExceptionOnDelete() throws IOException {
        when(client.deleteByQuery(any(DeleteByQueryRequest.class))).thenThrow(new IOException("timeout"));

        assertThatThrownBy(() -> adapter.deleteByPublicationRevisionKey("pub-01"))
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> assertThat(((ProjectionException) ex).errorCode())
                        .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_CLEANUP));
    }

    @Test
    @DisplayName("countByPublicationRevisionKey: 返回计数")
    void shouldCountByPublicationRevisionKey() throws IOException {
        CountResponse response = mock(CountResponse.class);
        when(response.count()).thenReturn(42L);
        when(client.count(any(CountRequest.class))).thenReturn(response);

        long count = adapter.countByPublicationRevisionKey("pub-01");

        assertThat(count).isEqualTo(42L);
    }

    @Test
    @DisplayName("countByPublicationRevisionKey: IO 异常包装为 PROJECTION_QUERY_ERROR")
    void shouldWrapIOExceptionOnCount() throws IOException {
        when(client.count(any(CountRequest.class))).thenThrow(new IOException("network"));

        assertThatThrownBy(() -> adapter.countByPublicationRevisionKey("pub-01"))
                .isInstanceOf(ProjectionException.class)
                .satisfies(ex -> assertThat(((ProjectionException) ex).errorCode())
                        .isEqualTo(ElasticsearchPublicationProjection.ERR_PROJECTION_QUERY));
    }

    // ────────────────────────────── 辅助 ──────────────────────────────

    private ProjectionRequest buildSampleRequest(int chunkCount) {
        List<ChunkProjection> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            chunks.add(new ChunkProjection(
                    "chunk-" + i,
                    i == 0 ? null : "chunk-0",
                    i == 0 ? ChunkProjection.LEVEL_PARENT : ChunkProjection.LEVEL_CHILD,
                    i,
                    "raw-" + i,
                    "display-" + i,
                    "embed-" + i,
                    "第1章/1." + i,
                    "PAGE",
                    1,
                    null,
                    null,
                    null));
        }
        return new ProjectionRequest(
                "pub-01", "ws-01", "kb-01", "doc-01",
                "parse-01", "chunk-01", "sha256:abc", chunks);
    }

    /**
     * 构造 BulkResponseItem。由于 builder 强制要求 operationType 等字段，
     * 这里通过 mock 绕过 builder 校验。
     */
    private BulkResponseItem bulkItem(String id, boolean hasError, String errorReason) {
        BulkResponseItem item = mock(BulkResponseItem.class);
        when(item.id()).thenReturn(id);
        if (hasError) {
            co.elastic.clients.elasticsearch._types.ErrorCause cause =
                    co.elastic.clients.elasticsearch._types.ErrorCause.of(e -> e.reason(errorReason));
            when(item.error()).thenReturn(cause);
        } else {
            when(item.error()).thenReturn(null);
        }
        return item;
    }

    /**
     * 构造 GetMappingResponse，包含指定 dims 的 vector dense_vector 字段（T042）。
     *
     * <p>使用 mock 绕过 {@code GetMappingResponse.Builder} 在 8.10.4 的 API 差异。
     */
    private GetMappingResponse buildMappingResponse(int dims) {
        Property vectorProperty = Property.of(p -> p
                .denseVector(dv -> dv.dims(dims)));
        TypeMapping mapping = TypeMapping.of(m -> m
                .properties(java.util.Map.of("vector", vectorProperty)));
        IndexMappingRecord record = IndexMappingRecord.of(r -> r.mappings(mapping));
        GetMappingResponse response = mock(GetMappingResponse.class);
        when(response.get(ElasticsearchPublicationProjection.PHYSICAL_INDEX))
                .thenReturn(record);
        return response;
    }

    /**
     * 构造不含 vector 字段的 GetMappingResponse（T042）。
     */
    private GetMappingResponse buildMappingResponseWithoutVector() {
        TypeMapping mapping = TypeMapping.of(m -> m
                .properties(java.util.Map.of("chunkKey", Property.of(p -> p.keyword(k -> k)))));
        IndexMappingRecord record = IndexMappingRecord.of(r -> r.mappings(mapping));
        GetMappingResponse response = mock(GetMappingResponse.class);
        when(response.get(ElasticsearchPublicationProjection.PHYSICAL_INDEX))
                .thenReturn(record);
        return response;
    }
}
