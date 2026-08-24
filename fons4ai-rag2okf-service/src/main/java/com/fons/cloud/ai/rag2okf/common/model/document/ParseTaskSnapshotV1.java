package com.fons.cloud.ai.rag2okf.common.model.document;

import com.fons.cloud.ai.rag2okf.common.constants.document.ParserType;

import java.util.Date;
import java.util.Map;

/**
 * PARSE 任务输入快照 v1 公共契约。
 *
 * <p>任务创建时冻结本次解析输入的最小事实（设计 §4.4）：只保存业务 key
 * 引用与非秘密参数；模型档案只冻结 {@code profileKey} 与绑定版本提示，
 * 不复制 Base URL、API Key 或连接密文，运行时由用户域安全解析并校验
 * ACTIVE/归属后使用。</p>
 *
 * <p>本对象为不可变契约 record：Map 组件在构造时防御性复制，构造后外界
 * 修改原始集合不影响快照内容。{@code schemaVersion} 固定为
 * {@link #SCHEMA_VERSION}，与 {@code kb_processing_task.snapshot_version}
 * 列取值一致，未来结构变化时以 v2 版本演进，不原地改写 v1 字段。</p>
 *
 * <p>RECHUNK/PUBLISH/DELETE_CLEANUP 的快照契约结构不同，归后续任务包
 * 各自定义，不复用本类型。</p>
 *
 * @author hongqy
 */
public record ParseTaskSnapshotV1(
        /** 快照结构版本，恒为 {@link #SCHEMA_VERSION}。 */
        String schemaVersion,
        /** 所属工作空间业务 key。 */
        String workspaceKey,
        /** 所属知识库业务 key。 */
        String knowledgeBaseKey,
        /** 目标文档业务 key。 */
        String documentKey,
        /** 本次解析目标源文件的 CAS 令牌，任务执行前校验其仍为当前文件。 */
        String sourceFileToken,
        /** 解析器类型，任务创建时冻结，本期只允许 BUILT_IN。 */
        ParserType parserType,
        /** 解析器非秘密参数，可空。 */
        Map<String, Object> parserOptions,
        /** 分块策略快照，任务创建时冻结。 */
        ChunkPolicySnapshot chunkPolicy,
        /** 模型档案引用集合，key 为用途标识（如 EMBEDDING），值只含业务 key 引用。 */
        Map<String, ModelProfileRef> modelProfileRefs,
        /** 解析加首次分块成功后是否自动继续发布，本次操作意图。 */
        boolean publishAfterSuccess,
        /** 发起用户主键。 */
        Long requestedBy,
        /** 发起时间。 */
        Date requestedAt) {

    /** 当前快照结构版本。 */
    public static final String SCHEMA_VERSION = "1.0";

    /**
     * 紧凑构造器：完成 Map 组件的防御性复制，保证快照构造后不可变。
     */
    public ParseTaskSnapshotV1 {
        parserOptions = parserOptions == null ? null : Map.copyOf(parserOptions);
        modelProfileRefs = modelProfileRefs == null ? null : Map.copyOf(modelProfileRefs);
    }

    /**
     * 构造当前版本的 PARSE 任务快照。
     *
     * <p>{@code schemaVersion} 自动取 {@link #SCHEMA_VERSION}，调用方无需
     * 传入，避免快照结构版本与实体 {@code snapshot_version} 列不一致。</p>
     *
     * @param workspaceKey      所属工作空间业务 key
     * @param knowledgeBaseKey  所属知识库业务 key
     * @param documentKey       目标文档业务 key
     * @param sourceFileToken   源文件 CAS 令牌
     * @param parserType        解析器类型
     * @param parserOptions     解析器非秘密参数，可为 {@code null}
     * @param chunkPolicy       分块策略快照
     * @param modelProfileRefs  模型档案引用集合，可为 {@code null}
     * @param publishAfterSuccess 成功后是否自动发布
     * @param requestedBy       发起用户主键
     * @param requestedAt       发起时间
     * @return 已冻结的解析任务快照
     */
    public static ParseTaskSnapshotV1 of(
            String workspaceKey,
            String knowledgeBaseKey,
            String documentKey,
            String sourceFileToken,
            ParserType parserType,
            Map<String, Object> parserOptions,
            ChunkPolicySnapshot chunkPolicy,
            Map<String, ModelProfileRef> modelProfileRefs,
            boolean publishAfterSuccess,
            Long requestedBy,
            Date requestedAt) {
        return new ParseTaskSnapshotV1(SCHEMA_VERSION, workspaceKey, knowledgeBaseKey, documentKey,
                sourceFileToken, parserType, parserOptions, chunkPolicy, modelProfileRefs,
                publishAfterSuccess, requestedBy, requestedAt);
    }

    /**
     * 分块策略快照。
     *
     * <p>只冻结策略标识与非秘密参数：{@code boundaryType} 白名单
     * LENGTH/STRUCTURE/SEMANTIC，{@code hierarchyType} 白名单
     * FLAT/PARENT_CHILD，非法值在任务创建预检时拒绝，不做默认回退。</p>
     *
     * @author hongqy
     */
    public record ChunkPolicySnapshot(
            /** 分块边界策略：LENGTH、STRUCTURE、SEMANTIC。 */
            String boundaryType,
            /** 分块层级策略：FLAT、PARENT_CHILD。 */
            String hierarchyType,
            /** 策略非秘密参数，如 targetSize/maxSize/overlap，可空。 */
            Map<String, Object> parameters) {

        /**
         * 紧凑构造器：完成参数 Map 的防御性复制。
         */
        public ChunkPolicySnapshot {
            parameters = parameters == null ? null : Map.copyOf(parameters);
        }
    }

    /**
     * 模型档案引用。
     *
     * <p>只冻结业务 key 与绑定版本提示：运行时按 {@code profileKey} 由用户域
     * 安全解析并校验 ACTIVE/归属，引用被停用或撤销时任务 fail-closed；
     * 任何 Base URL、API Key、连接密文都不得出现在本对象中。</p>
     *
     * @author hongqy
     */
    public record ModelProfileRef(
            /** 模型档案业务 key，执行时安全解析。 */
            String profileKey,
            /** 绑定版本提示，用于执行期复核引用未变化，可空。 */
            String bindingVersion) {
    }
}
