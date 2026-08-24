-- =====================================================================
-- 文档处理流水线重构 TP-002 / T007：文档域 3 表执行型 DDL 草案
-- ---------------------------------------------------------------------
-- 结构依据：spec/features/20260817/document-processing-refactor/
--           文档处理流水线重构-技术设计说明书.md §5.3 结构变更详设（V2.0.2）
-- 命名与列顺序：沿用 fons4ai-rag2okf-service/sql/init-schema.sql 中
--           kb_source_document / kb_processing_task 的既有风格。
-- 执行方式：项目无 Flyway，应用启动不执行 DDL；本草案由用户/DBA 审核
--           后手工执行，执行状态确认与 SQL 知识快照同步归 T013。
-- ---------------------------------------------------------------------
-- 执行前置条件：
--   1. 仅重建文档域 3 张表：kb_document、kb_document_result、kb_processing_task；
--   2. 用户与知识库域表（kb_user、kb_workspace、kb_workspace_member、
--      kb_knowledge_base、kb_model_connection、kb_model_profile、
--      kb_model_binding、kb_outbox_event）不在本脚本范围内，保持不动；
--   3. 旧文档域表（kb_source_document、kb_parse_revision、
--      kb_chunk_revision、kb_publication_revision）本脚本不做任何
--      DROP/ALTER，其退出归 TP-008 切换脚本；
--   4. 开发期无生产文档数据，重建即清空，不做旧数据迁移（需求已确认）。
-- ---------------------------------------------------------------------
-- 回滚说明：
--   1. DROP TABLE IF EXISTS kb_document;
--      DROP TABLE IF EXISTS kb_document_result;
--   2. 按旧结构重建 kb_processing_task：照抄
--      fons4ai-rag2okf-service/sql/init-schema.sql 中 kb_processing_task
--      的原始定义（payload_json / input_revision_key 旧列结构）；
--   3. 回滚不承诺恢复已清空的文档域数据（用户已确认无生产文档数据）。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 1. kb_document：文档身份、用户可见状态与删除审计
--    文件元数据与制品指针不在本表，统一归 kb_document_result。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS kb_document;
CREATE TABLE kb_document (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    document_key CHAR(26) NOT NULL COMMENT '文档稳定业务标识，同名文档各占独立 key，天然并存不作唯一性合并',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库主键',
    display_name VARCHAR(255) NOT NULL COMMENT '文档展示名称，默认取上传文件名，仅展示用，不参与唯一约束',
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADED' COMMENT '用户可见状态白名单：UPLOADED、PARSED、PUBLISHED、FAILED、DELETED',
    cleanup_status VARCHAR(20) NOT NULL DEFAULT 'NOT_REQUIRED' COMMENT '内容清理状态白名单：NOT_REQUIRED、PENDING、SUCCEEDED、FAILED',
    deleted_by BIGINT NULL COMMENT '执行删除的用户主键，仅删除受理后写入',
    deleted_at DATETIME(3) NULL COMMENT '业务删除受理时间，仅删除受理后写入',
    created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    -- 业务标识唯一：上传应用服务生成，同名文档各占独立 key
    UNIQUE KEY uk_kb_document_key (document_key),
    -- 场景：文档列表按知识库分页查询活跃文档并按 updated 倒序
    KEY idx_kb_document_kb_deleted_updated (knowledge_base_id, deleted, updated),
    -- 场景：检索可见性按 PUBLISHED 批量核对、管理端按用户可见状态筛选
    KEY idx_kb_document_status (status),
    -- 场景：TP-006 清理任务扫描 PENDING/FAILED 待清理文档
    KEY idx_kb_document_cleanup_status (cleanup_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档身份、用户可见状态与删除审计';

-- ---------------------------------------------------------------------
-- 2. kb_document_result：结果生命周期、源文件指针与制品登记
--    stage 只允许 INIT→PARSE→CHUNK→PUBLISH 顺序推进；
--    失败原因不在本表，统一归 kb_processing_task。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS kb_document_result;
CREATE TABLE kb_document_result (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    result_key CHAR(26) NOT NULL COMMENT '结果业务标识，每次新建结果时生成',
    document_id BIGINT NOT NULL COMMENT '所属文档主键',
    source_file_token CHAR(26) NOT NULL COMMENT '当前源文件 CAS 令牌，每次上传重新生成',
    source_object_key VARCHAR(512) NOT NULL COMMENT '当前源文件 MinIO 对象键，结构为 sources/{fileToken}/{sanitizedFilename}',
    source_original_filename VARCHAR(255) NOT NULL COMMENT '上传时原始文件名',
    source_content_type VARCHAR(128) NOT NULL COMMENT '服务端校验后的 MIME 类型',
    source_size_bytes BIGINT NOT NULL COMMENT '源文件字节数，流式写入时统计',
    source_sha256 CHAR(64) NOT NULL COMMENT '源文件 SHA-256 摘要，流式写入时计算',
    source_upload_actor_id BIGINT NOT NULL COMMENT '上传用户主键',
    stage VARCHAR(20) NOT NULL DEFAULT 'INIT' COMMENT '结果生命周期阶段白名单：INIT、PARSE、CHUNK、PUBLISH，只允许顺序推进',
    parser_type VARCHAR(20) NULL COMMENT '解析器类型白名单：BUILT_IN、MINERU；INIT 阶段为空，PARSE 任务创建后冻结',
    parser_snapshot_json JSON NULL COMMENT '任务创建时冻结的解析器、模型引用与非秘密参数快照',
    parsed_document_object_key VARCHAR(512) NULL COMMENT 'ParsedDocument v1 JSON 的 MinIO 对象键，解析成功并通过 Schema 校验后写入',
    parsed_markdown_object_key VARCHAR(512) NULL COMMENT '派生 Markdown 的 MinIO 对象键，可选',
    block_count INT NOT NULL DEFAULT 0 COMMENT '结构块数量，解析完成时写入',
    warning_count INT NOT NULL DEFAULT 0 COMMENT '解析警告数量，解析完成时写入',
    boundary_type VARCHAR(20) NULL COMMENT '分块边界策略白名单：LENGTH、STRUCTURE、SEMANTIC；PARSE 后写入',
    hierarchy_type VARCHAR(20) NULL COMMENT '分块层级策略白名单：FLAT、PARENT_CHILD；PARSE 后写入',
    policy_snapshot_json JSON NULL COMMENT '任务创建时冻结的分块参数与语义模型引用快照',
    chunk_manifest_object_key VARCHAR(512) NULL COMMENT 'ChunkManifest v1 的 MinIO 对象键，分块成功并校验后写入',
    parent_count INT NOT NULL DEFAULT 0 COMMENT '父分块数量',
    child_count INT NOT NULL DEFAULT 0 COMMENT '子分块数量',
    total_count INT NOT NULL DEFAULT 0 COMMENT '分块总数',
    content_hash CHAR(64) NULL COMMENT '规范化分块集合内容摘要，分块成功时计算',
    embedding_profile_key CHAR(26) NULL COMMENT '向量模型档案业务标识引用，发布时冻结',
    embedding_dimensions INT NULL COMMENT '向量实际验证维度，模型返回校验后写入',
    projection_ref_json JSON NULL COMMENT 'ES 投影安全引用，writeProjection 成功后写入',
    projection_count INT NOT NULL DEFAULT 0 COMMENT '投影文档数，validateWrite 成功后写入',
    published_at DATETIME(3) NULL COMMENT '发布成功时间',
    created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本，advanceStage 以 expectedResultVersion 做 CAS',
    PRIMARY KEY (id),
    -- 业务标识唯一：每次新建 result 时生成
    UNIQUE KEY uk_kb_document_result_key (result_key),
    -- 场景：MinIO 对象键唯一登记，不同文档、不同 fileToken 不共享对象键
    UNIQUE KEY uk_kb_document_result_source_object (source_object_key),
    -- 场景：解析制品对象键唯一登记，防止重复写入指向同一对象
    UNIQUE KEY uk_kb_document_result_parsed_document (parsed_document_object_key),
    -- 场景：派生 Markdown 对象键唯一登记
    UNIQUE KEY uk_kb_document_result_parsed_markdown (parsed_markdown_object_key),
    -- 场景：分块制品对象键唯一登记
    UNIQUE KEY uk_kb_document_result_chunk_manifest (chunk_manifest_object_key),
    -- 场景：详情装配当前 result 与 TP-006 清理按 document_id 枚举制品对象键
    KEY idx_kb_document_result_document_stage (document_id, deleted, stage),
    -- 场景：源文件 CAS 令牌追溯，同 token 定位当前源文件记录
    KEY idx_kb_document_result_file_token (source_file_token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档结果生命周期、源文件指针与制品登记，靠 stage 顺序推进';

-- ---------------------------------------------------------------------
-- 3. kb_processing_task：异步任务、幂等、租约与安全失败原因
--    在旧表基础上重建：payload_json→snapshot_json、
--    input_revision_key→input_result_key、新增 snapshot_version 与
--    retry_of_task_id、task_type 扩展 DELETE_CLEANUP。
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS kb_processing_task;
CREATE TABLE kb_processing_task (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    task_key CHAR(26) NOT NULL COMMENT '异步任务业务标识',
    workspace_id BIGINT NOT NULL COMMENT '所属工作空间主键',
    knowledge_base_id BIGINT NOT NULL COMMENT '所属知识库主键',
    source_document_id BIGINT NOT NULL COMMENT '目标文档主键',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型严格枚举：PARSE、RECHUNK、PUBLISH、DELETE_CLEANUP，不接受未知类型',
    input_result_key CHAR(26) NULL COMMENT '任务输入 kb_document_result.result_key 引用',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '调用方幂等键，一次用户操作提供一个',
    retry_of_task_id BIGINT NULL COMMENT '重新发起或清理重试时关联的原失败任务主键，用于追溯',
    snapshot_version VARCHAR(16) NOT NULL COMMENT '任务快照版本号，与 snapshot_json 的 schemaVersion 一致，如 1.0',
    snapshot_json JSON NULL COMMENT '任务输入版本化最小快照，只冻结非秘密引用',
    status VARCHAR(20) NOT NULL COMMENT '任务状态：QUEUED、RUNNING、SUCCEEDED、RETRY_WAIT、FAILED',
    stage VARCHAR(32) NULL COMMENT '当前执行阶段',
    progress INT NOT NULL DEFAULT 0 COMMENT '进度百分比，范围 0 到 100',
    attempt INT NOT NULL DEFAULT 0 COMMENT '已执行次数',
    max_attempts INT NOT NULL DEFAULT 3 COMMENT '最大执行次数',
    next_run_at DATETIME(3) NULL COMMENT '下次候选执行时间',
    execution_owner VARCHAR(128) NULL COMMENT '最近一次执行实例标识，不表示数据库锁',
    heartbeat_at DATETIME(3) NULL COMMENT '最近执行心跳时间',
    execution_deadline DATETIME(3) NULL COMMENT '执行租约恢复期限，不表示数据库锁',
    error_code VARCHAR(64) NULL COMMENT '安全化错误码，失败原因事实源',
    error_message VARCHAR(500) NULL COMMENT '安全化错误摘要，失败原因事实源',
    created DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
    PRIMARY KEY (id),
    -- 业务标识唯一
    UNIQUE KEY uk_kb_processing_task_key (task_key),
    -- 场景：幂等创建查重，同一文档同一类型同一幂等键只允许一个任务
    UNIQUE KEY uk_kb_processing_task_idempotency (source_document_id, task_type, idempotency_key),
    -- 场景：调度器扫描到期任务（status + next_run_at）
    KEY idx_kb_processing_task_status_next_run (status, next_run_at),
    -- 场景：详情页 latestTasks 按文档取每类型最近一条任务
    KEY idx_kb_processing_task_document_created (source_document_id, created),
    -- 场景：重新发起或清理重试时按原失败任务追溯链路
    KEY idx_kb_processing_task_retry_of (retry_of_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异步任务、幂等、租约恢复与安全失败原因事实';
