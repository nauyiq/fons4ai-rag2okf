package com.fons.cloud.ai.rag2okf.common.request.document;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 管理员基于当前 ParsedDocument 发起重新分块的请求。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DocumentRechunkRequest {

    /** 调用方读取当前结果时看到的版本，作为创建和执行的 CAS 前提。 */
    @NotNull
    private Integer expectedResultVersion;

    /** 冻结到 RECHUNK 任务的两维分块策略。 */
    @NotNull
    @Valid
    private ChunkPolicyRequest chunkPolicy;
}
